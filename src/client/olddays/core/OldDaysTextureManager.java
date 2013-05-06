package net.minecraft.src;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class OldDaysTextureManager{
    private mod_OldDays core;
    private RenderEngine renderEngine;
    protected ArrayList<TextureHook> textureHooks;
    protected ArrayList<TextureStitched> textureFXList;
    private String currentpack;
    private HashMap<String, Boolean> entryCache;
    private Texture tmp;
    private int tmpWidth;
    private int tmpHeight;

    public OldDaysTextureManager(mod_OldDays olddays){
        core = olddays;
        renderEngine = mod_OldDays.getMinecraft().renderEngine;
        textureHooks = new ArrayList<TextureHook>();
        textureFXList = new ArrayList<TextureStitched>();
        entryCache = new HashMap<String, Boolean>();
    }

    public void setTextureHook(String origname, String newname, boolean b){
        for (int i = 0; i < textureHooks.size(); i++){
            TextureHook hook = textureHooks.get(i);
            if (hook.origname.equals(origname) && hook.newname.equals(newname)){
                hook.enabled = b;
                refreshTextureHooks();
                return;
            }
        }
        textureHooks.add(new TextureHook(origname, newname, b));
        refreshTextureHooks();
    }
 
    public void onTick(){
        if (currentpack==null || currentpack!=mod_OldDays.getMinecraft().gameSettings.skin){
            currentpack=mod_OldDays.getMinecraft().gameSettings.skin;
            entryCache.clear();
            core.refreshTextures();
            setFallback(!hasEntry("olddays"));
        }
    }

    public void refreshTextureHooks(){
        for (TextureHook hook : textureHooks){
            try{
                TexturePackList packList = mod_OldDays.getMinecraft().texturePackList;
                ITexturePack texpack = ((ITexturePack)mod_OldDays.getField(TexturePackList.class, packList, 6));
                BufferedImage image = ImageIO.read(texpack.getResourceAsStream(hook.enabled ? hook.newname : hook.origname));
                int id = 0;
                try{
                    Method m = null;
                    Method[] methods = (RenderEngine.class).getDeclaredMethods();
                    for (int i = 0; i < methods.length; i++){
                        if (methods[i].toGenericString().matches("^private int (net.minecraft.src.)?[a-zA-Z]{1,12}.[a-zA-Z]{1,10}.java.lang.String.$")){
                            m = methods[i];
                            break;
                        }
                    }
                    if (m == null){
                        return;
                    }
                    m.setAccessible(true);
                    id = (Integer)(m.invoke(renderEngine, hook.origname));
                }catch(Exception ex){
                    ex.printStackTrace();
                }
                renderEngine.setupTexture(image, id);
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }
    }

    private void setFallback(boolean b){
        for (int i = 0; i < mod_OldDays.modules.size(); i++){
            OldDaysModule module = mod_OldDays.modules.get(i);
            for (int j = 1; j <= module.properties.size(); j++){
                if (module.getPropertyById(j).shouldRefreshOnFallback()){
                    mod_OldDays.sendCallback(module.id, j);
                }
            }
            module.onFallbackChange(b);
        }
    }

    public boolean hasEntry(String... str){
        try{
            TexturePackList packList = mod_OldDays.getMinecraft().texturePackList;
            ITexturePack texpack = ((ITexturePack)mod_OldDays.getField(TexturePackList.class, packList, 6));
            for (int i = 0; i < str.length; i++){
                if (entryCache.containsKey(str[i])){
                    if (!entryCache.get(str[i])){
                        return false;
                    }
                }else if (texpack instanceof TexturePackDefault){
                    try{
                        texpack.getResourceAsStream("/" + str[i]);
                    }catch(Exception e){
                        entryCache.put(str[i], false);
                        return false;
                    }
                    entryCache.put(str[i], true);
                }else if (texpack instanceof TexturePackFolder){
                    File orig = ((File)mod_OldDays.getField(TexturePackImplementation.class, texpack, 2));
                    File file = new File(orig, str[i]);
                    boolean b = file.exists();
                    entryCache.put(str[i], b);
                    if (!b){
                        return false;
                    }
                }else{
                    ZipFile file = ((ZipFile)mod_OldDays.getField(TexturePackCustom.class, texpack, 0));
                    boolean b = file.getEntry(str[i]) != null;
                    entryCache.put(str[i], b);
                    if (!b){
                        return false;
                    }
                }
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return true;
    }

    public boolean hasIcons(boolean items, String... str){
        for (int i = 0; i < str.length; i++){
            str[i] = "textures/" + (items ? "items" : "blocks") + "/" + str[i] + ".png";
        }
        return hasEntry(str);
    }

    public Icon registerCustomIcon(IconRegister map, String par1Str, TextureStitched icon, Icon from){
        if (par1Str == null || !(map instanceof TextureMap)){
            return icon;
        }
        Map textureStitchedMap = (Map)(mod_OldDays.getField(TextureMap.class, (TextureMap)map, 9));
        if (textureStitchedMap == null){
            return icon;
        }
        TextureStitched texturestitched = (TextureStitched)textureStitchedMap.get(par1Str);
        if (texturestitched == null || texturestitched != icon)
        {
            textureStitchedMap.put(par1Str, icon);
        }
        if (icon instanceof TextureFX){
            for (TextureStitched fx : textureFXList){
                if (fx.getIconName().equals(par1Str)){
                    textureFXList.remove(fx);
                    break;
                }
            }
            textureFXList.add(icon);
        }
        if (from != null){
            try{
                icon.copyFrom((TextureStitched)from);
            }catch(Exception e){}
        }
        return icon;
    }

    public Icon registerCustomIcon(IconRegister map, String par1Str, TextureStitched icon){
        return registerCustomIcon(map, par1Str, icon, null);
    }

    public void updateTextureFXes(){
        for (TextureStitched fx : textureFXList){
            try{
                fx.updateAnimation();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public void removeTextureFXes(){
        renderEngine.refreshTextures();
        textureFXList.clear();
        System.gc();
        renderEngine.updateDynamicTextures();
    }

    public void eraseIcon(Icon icon, String origIcon, boolean b){
        if (icon == null){
            return;
        }
        if (b){
            replaceIcon(icon, "", 0, 0, origIcon, false);
            return;
        }
        Texture sheet = (Texture)(mod_OldDays.getField(TextureStitched.class, icon, 1));
        boolean rot = (Boolean)(mod_OldDays.getField(TextureStitched.class, icon, 4));
        int width = (Integer)(mod_OldDays.getField(TextureStitched.class, icon, 7));
        int height = (Integer)(mod_OldDays.getField(TextureStitched.class, icon, 8));
        Texture tex = getTempTexture(width, height, true);
        sheet.copyFrom(icon.getOriginX(), icon.getOriginY(), tex, rot);
    }

    public void replaceIcon(Icon icon, String newIcon, int x, int y, String origIcon, boolean b){
        if (icon == null){
            return;
        }
        b = b && newIcon.length() > 0 && hasEntry(newIcon.substring(1));
        if (!b){
            x = 0;
            y = 0;
            newIcon = origIcon;
        }
        Texture sheet = (Texture)(mod_OldDays.getField(TextureStitched.class, icon, 1));
        boolean rot = (Boolean)(mod_OldDays.getField(TextureStitched.class, icon, 4));
        int width = (Integer)(mod_OldDays.getField(TextureStitched.class, icon, 7));
        int height = (Integer)(mod_OldDays.getField(TextureStitched.class, icon, 8));
        int[] ints = new int[width * height];
        try{
            TexturePackList packList = mod_OldDays.getMinecraft().texturePackList;
            ITexturePack texpack = ((ITexturePack)mod_OldDays.getField(TexturePackList.class, packList, 6));
            ImageIO.read(texpack.getResourceAsStream(newIcon)).getRGB(x * width, y * height, width, height, ints, 0, width);
        }catch(Exception e){
            e.printStackTrace();
        }
        Texture tex = getTempTexture(width, height, false);
        tex.getTextureData().position(0);
        for (int i = 0; i < ints.length; i++){
            int color = ints[i];
            tex.getTextureData().put((byte)(color >> 16 & 0xFF));
            tex.getTextureData().put((byte)(color >> 8 & 0xFF));
            tex.getTextureData().put((byte)(color & 0xFF));
            tex.getTextureData().put((byte)(color >> 24 & 0xFF));
        }
        tex.getTextureData().clear();
        sheet.copyFrom(icon.getOriginX(), icon.getOriginY(), tex, rot);
    }

    public boolean copyIconFromSheet(Icon icon, String str, HashMap<String, Integer> indexMap){
        if (!hasEntry(str.substring(1)) || icon == null || indexMap == null || !indexMap.containsKey(icon.getIconName())){
            return false;
        }
        int index = indexMap.get(icon.getIconName());
        if (index < 0 || index >= 256){
            return false;
        }
        int x = index % 16;
        int y = index / 16;
        Texture sheet = (Texture)(mod_OldDays.getField(TextureStitched.class, icon, 1));
        int[] ints = null;
        int width = 16;
        int height = 16;
        try{
            TexturePackList packList = mod_OldDays.getMinecraft().texturePackList;
            ITexturePack texpack = ((ITexturePack)mod_OldDays.getField(TexturePackList.class, packList, 6));
            BufferedImage image = ImageIO.read(texpack.getResourceAsStream(str));
            width = image.getWidth() / 16;
            height = image.getHeight() / 16;
            ints = new int[width * height];
            image.getRGB(x * width, y * height, width, height, ints, 0, width);
            image = null;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        Texture tmp = getTempTexture(width, height, false);
        tmp.getTextureData().position(0);
        for (int i = 0; i < ints.length; i++){
            int color = ints[i];
            tmp.getTextureData().put((byte)(color >> 16 & 0xFF));
            tmp.getTextureData().put((byte)(color >> 8 & 0xFF));
            tmp.getTextureData().put((byte)(color & 0xFF));
            tmp.getTextureData().put((byte)(color >> 24 & 0xFF));
        }
        tmp.getTextureData().clear();
        boolean rot = (Boolean)(mod_OldDays.getField(TextureStitched.class, icon, 4));
        sheet.copyFrom(icon.getOriginX(), icon.getOriginY(), tmp, rot);
        return true;
    }

    private Texture getTempTexture(int width, int height, boolean erase){
        if (tmp != null && width == tmpWidth && height == tmpHeight){
            if (erase){
                ByteBuffer b = tmp.getTextureData();
                b.position(0);
                b.put(new byte[width * height * 4]);
                b.clear();
            }
            return tmp;
        }
        tmpWidth = width;
        tmpHeight = height;
        tmp = new Texture("", 2, width, height, 10496, GL11.GL_RGBA, 9728, 9728, 0, null);
        return tmp;
    }

    private class TextureHook{
        private String origname;
        private String newname;
        private boolean enabled;

        public TextureHook(String str1, String str2, boolean b){
            origname = str1;
            newname = str2;
            enabled = b;
        }
    }
}