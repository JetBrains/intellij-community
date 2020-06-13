// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorHexUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.IconUIResource;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static com.intellij.util.ui.JBUI.Borders.customLine;
import static com.intellij.util.ui.JBUI.asUIResource;

/**
 * @author Konstantin Bulenkov
 */
public class UITheme {
  public static final String FILE_EXT_ENDING = ".theme.json";

  private static final Logger LOG = Logger.getInstance(UITheme.class);

  private String name;
  private boolean dark;
  private String author;
  private String id;
  private String editorScheme;
  private Map<String, Object> ui;
  private Map<String, Object> icons;
  private IconPathPatcher patcher;
  private Map<String, Object> background;
  private Map<String, Object> emptyFrameBackground;
  private Map<String, Object> colors;
  private ClassLoader providerClassLoader = getClass().getClassLoader();
  private String editorSchemeName;
  private SVGLoader.SvgElementColorPatcherProvider colorPatcher;

  private UITheme() { }

  public String getName() {
    return name;
  }

  public boolean isDark() {
    return dark;
  }

  public String getAuthor() {
    return author;
  }

  public URL getResource(String path) {
    if (isTempTheme()) {
      File file = new File(path);
      if (file.exists()) {
        try {
          return file.toURI().toURL();
        }
        catch (MalformedURLException e) {
          LOG.warn(e);
        }
      }
    }
    return getProviderClassLoader().getResource(path);
  }

  public InputStream getResourceAsStream(String path) {
    URL url = getResource(path);
    try {
      return url != null ? url.openStream() : null;
    } catch (IOException e) {
      return null;
    }
  }

  private boolean isTempTheme() {
    return "Temp theme".equals(id);
  }

  @NotNull
  public static UITheme loadFromJson(@NotNull InputStream stream, @NotNull String themeId, @Nullable ClassLoader provider) throws IllegalStateException {
    return loadFromJson(stream, themeId, provider, s -> s);
  }

  @NotNull
  public static UITheme loadFromJson(@NotNull InputStream stream,
                                     @NotNull String themeId,
                                     @Nullable ClassLoader provider,
                                     @NotNull Function<? super String, String> iconsMapper) throws IllegalStateException {
    UITheme theme = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), UITheme.class);
    theme.id = themeId;

    if (provider != null) {
      theme.providerClassLoader = provider;
    }

    if (theme.icons != null && !theme.icons.isEmpty()) {
      theme.patcher = new IconPathPatcher() {
        @Nullable
        @Override
        public String patchPath(@NotNull String path, ClassLoader classLoader) {
          if (classLoader instanceof PluginClassLoader) {
            String pluginId = ((PluginClassLoader)classLoader).getPluginId().getIdString();
            Object icons = theme.icons.get(pluginId);
            if (icons instanceof Map) {
              Object pluginIconPath = ((Map)icons).get(path);
              if (pluginIconPath instanceof String) {
                return iconsMapper.apply((String)pluginIconPath);
              }
            }
          }

          Object value = theme.icons.get(path);
          return value instanceof String ? iconsMapper.apply((String)value) : null;
        }

        @Nullable
        @Override
        public ClassLoader getContextClassLoader(@NotNull String path, ClassLoader originalClassLoader) {
          return theme.providerClassLoader;
        }
      };

      Object palette = theme.icons.get("ColorPalette");
      if (palette instanceof Map) {
        Map colors = (Map)palette;
        PaletteScopeManager paletteScopeManager = new PaletteScopeManager();
        for (Object o : colors.keySet()) {
          String colorKey = o.toString();
          PaletteScope scope = paletteScopeManager.getScope(colorKey);
          if (scope == null) continue;
          String key = toColorString(colorKey, theme.isDark());
          Object v = colors.get(colorKey);
          if (v instanceof String) {
            String value = (String)v;
            String alpha = null;
            if (value.length() == 9) {
              alpha = value.substring(7);
              value = value.substring(0, 7);
            }
            if (ColorUtil.fromHex(key, null) != null && ColorUtil.fromHex(value, null) != null) {
              scope.newPalette.put(key, value);
              int fillTransparency = -1;
              if (alpha != null) {
                try {
                  fillTransparency = Integer.parseInt(alpha, 16);
                }
                catch (Exception ignore) { }
              }
              if (fillTransparency != -1) {
                scope.alphas.put(value, fillTransparency);
              }
            }
          }
        }

        theme.colorPatcher = new SVGLoader.SvgElementColorPatcherProvider() {
          @Nullable
          @Override
          public SVGLoader.SvgElementColorPatcher forURL(@Nullable URL url) {
            PaletteScope scope = paletteScopeManager.getScopeByURL(url);
            if (scope == null) {
              return null;
            }

            byte[] digest = scope.digest();
            Map<String, String> newPalette = scope.newPalette;
            Map<String, Integer> alphas = scope.alphas;
            return newPatcher(digest, newPalette, alphas);
          }

          @Nullable
          private SVGLoader.SvgElementColorPatcher newPatcher(byte @Nullable [] digest,
                                                              @NotNull Map<String, String> newPalette,
                                                              @NotNull Map<String, Integer> alphas) {
            if (newPalette.isEmpty()) {
              return null;
            }

            return new SVGLoader.SvgElementColorPatcher() {
              @Override
              public byte[] digest() {
                return digest;
              }

              @Override
              public void patchColors(@NotNull Element svg) {
                patchColorAttribute(svg, "fill");
                patchColorAttribute(svg, "stroke");
                NodeList nodes = svg.getChildNodes();
                int length = nodes.getLength();
                for (int i = 0; i < length; i++) {
                  Node item = nodes.item(i);
                  if (item instanceof Element) {
                    patchColors((Element)item);
                  }
                }
              }

              private void patchColorAttribute(@NotNull Element svg, String attrName) {
                String color = svg.getAttribute(attrName);
                if (color != null) {
                  String newColor = newPalette.get(StringUtil.toLowerCase(color));
                  if (newColor != null) {
                    svg.setAttribute(attrName, newColor);
                    if (alphas.get(newColor) != null) {
                      svg.setAttribute(attrName + "-opacity", String.valueOf((Float.valueOf(alphas.get(newColor)) / 255f)));
                    }
                  }
                }
              }
            };
          }
        };
      }
    }

    return theme;
  }

  private static String toColorString(String key, boolean darkTheme) {
    if (darkTheme && colorPalette.get(key + ".Dark") != null) {
      key += ".Dark";
    }
    String color = colorPalette.get(key);
    return color != null ? StringUtil.toLowerCase(color) : StringUtil.toLowerCase(key);
  }

  private static final @NonNls Map<String, String> colorPalette = new HashMap<>();
  static {
    colorPalette.put("Actions.Red", "#DB5860");
    colorPalette.put("Actions.Red.Dark", "#C75450");
    colorPalette.put("Actions.Yellow", "#EDA200");
    colorPalette.put("Actions.Yellow.Dark", "#F0A732");
    colorPalette.put("Actions.Green", "#59A869");
    colorPalette.put("Actions.Green.Dark", "#499C54");
    colorPalette.put("Actions.Blue", "#389FD6");
    colorPalette.put("Actions.Blue.Dark", "#3592C4");
    colorPalette.put("Actions.Grey", "#6E6E6E");
    colorPalette.put("Actions.Grey.Dark", "#AFB1B3");
    colorPalette.put("Actions.GreyInline", "#7F8B91");
    colorPalette.put("Actions.GreyInline.Dark", "#7F8B91");
    colorPalette.put("Objects.Grey", "#9AA7B0");
    colorPalette.put("Objects.Blue", "#40B6E0");
    colorPalette.put("Objects.Green", "#62B543");
    colorPalette.put("Objects.Yellow", "#F4AF3D");
    colorPalette.put("Objects.YellowDark", "#D9A343");
    colorPalette.put("Objects.Purple", "#B99BF8");
    colorPalette.put("Objects.Pink", "#F98B9E");
    colorPalette.put("Objects.Red", "#F26522");
    colorPalette.put("Objects.RedStatus", "#E05555");
    colorPalette.put("Objects.GreenAndroid", "#A4C639");
    colorPalette.put("Objects.BlackText", "#231F20");
    colorPalette.put("Checkbox.Background.Default", "#FFFFFF");
    colorPalette.put("Checkbox.Background.Default.Dark", "#43494A");
    colorPalette.put("Checkbox.Background.Disabled", "#F2F2F2");
    colorPalette.put("Checkbox.Background.Disabled.Dark", "#3C3F41");
    colorPalette.put("Checkbox.Border.Default", "#878787");
    colorPalette.put("Checkbox.Border.Default.Dark", "#6B6B6B");
    colorPalette.put("Checkbox.Border.Disabled", "#BDBDBD");
    colorPalette.put("Checkbox.Border.Disabled.Dark", "#545556");
    colorPalette.put("Checkbox.Focus.Thin.Default", "#7B9FC7");
    colorPalette.put("Checkbox.Focus.Thin.Default.Dark", "#466D94");
    colorPalette.put("Checkbox.Focus.Wide", "#97C3F3");
    colorPalette.put("Checkbox.Focus.Wide.Dark", "#3D6185");
    colorPalette.put("Checkbox.Foreground.Disabled", "#ABABAB");
    colorPalette.put("Checkbox.Foreground.Disabled.Dark", "#606060");
    colorPalette.put("Checkbox.Background.Selected", "#4D89C9");
    colorPalette.put("Checkbox.Background.Selected.Dark", "#43494A");
    colorPalette.put("Checkbox.Border.Selected", "#4982CC");
    colorPalette.put("Checkbox.Border.Selected.Dark", "#6B6B6B");
    colorPalette.put("Checkbox.Foreground.Selected", "#FFFFFF");
    colorPalette.put("Checkbox.Foreground.Selected.Dark", "#A7A7A7");
    colorPalette.put("Checkbox.Focus.Thin.Selected", "#ACCFF7");
    colorPalette.put("Checkbox.Focus.Thin.Selected.Dark", "#466D94");
    colorPalette.put("Tree.iconColor", "#808080");
    colorPalette.put("Tree.iconColor.Dark", "#AFB1B3");
  }

  public String getId() {
    return id;
  }

  @Nullable
  public String getEditorScheme() {
    return editorScheme;
  }

  public Map<String, Object> getBackground() {
    return background;
  }

  public Map<String, Object> getEmptyFrameBackground() {
    return emptyFrameBackground;
  }

  public void applyProperties(UIDefaults defaults) {
    if (ui == null) return;

    loadColorPalette(defaults);

    for (Map.Entry<String, Object> entry : ui.entrySet()) {
      apply(this, entry.getKey(), entry.getValue(), defaults);
    }
  }

  private void loadColorPalette(UIDefaults defaults) {
    if (colors != null) {
      for (Map.Entry<String, Object> entry : colors.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof String) {
          Color color = parseColor((String)value);
          if (color != null) {
            defaults.put("ColorPalette." + entry.getKey(), color);
          }
        }
      }
    }
  }

  public IconPathPatcher getPatcher() {
    return patcher;
  }

  public SVGLoader.SvgElementColorPatcherProvider getColorPatcher() {
    return colorPatcher;
  }

  @NotNull
  public ClassLoader getProviderClassLoader() {
    return providerClassLoader;
  }

  private static void apply(UITheme theme, String key, Object value, UIDefaults defaults) {
    if (value instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>)value;
      for (Map.Entry<String, Object> o : map.entrySet()) {
        apply(theme, key + "." + o.getKey(), o.getValue(), defaults);
      }
    }
    else {
      String valueStr = value.toString();
      Color color = null;
      if (theme.colors != null && theme.colors.containsKey(valueStr)) {
        color = parseColor(String.valueOf(theme.colors.get(valueStr)));
        if (color != null && !key.startsWith("*")) {
          defaults.put(key, color);
          return;
        }
      }
      value = color == null ? parseValue(key, valueStr) : color;
      if (key.startsWith("*.")) {
        String tail = key.substring(1);
        Object finalValue = value;
        addPattern(key, value, defaults);

        //please DO NOT stream on UIDefaults directly
        ((UIDefaults)defaults.clone()).keySet().stream()
          .filter(k -> k instanceof String && ((String)k).endsWith(tail))
          .forEach(k -> defaults.put(k, finalValue));
      }
      else {
        defaults.put(key, value);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void addPattern(String key, Object value, UIDefaults defaults) {
    Object o = defaults.get("*");
    if (!(o instanceof Map)) {
      o = new HashMap<String, Object>();
      defaults.put("*", o);
    }
    Map map = (Map)o;
    if (key != null && key.startsWith("*.")) {
      map.put(key.substring(2), value);
    }
  }

  public static Object parseValue(String key, @NotNull String value, @NotNull ClassLoader cl) {
    if ("null".equals(value)) return null;
    if ("true".equals(value)) return Boolean.TRUE;
    if ("false".equals(value)) return Boolean.FALSE;

    if (key.endsWith("Insets") || key.endsWith("padding")) {
      return parseInsets(value);
    }
    else if (key.endsWith("Border") || key.endsWith("border")) {
      try {
        List<String> ints = StringUtil.split(value, ",");
        if (ints.size() == 4) {
          return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
        }
        else if (ints.size() == 5) {
          return asUIResource(customLine(ColorUtil.fromHex(ints.get(4)),
                                         Integer.parseInt(ints.get(0)),
                                         Integer.parseInt(ints.get(1)),
                                         Integer.parseInt(ints.get(2)),
                                         Integer.parseInt(ints.get(3))));
        }
        Color color = ColorHexUtil.fromHexOrNull(value);
        if (color != null) {
          return asUIResource(customLine(color, 1));
        }
        else {
          return Class.forName(value, true, cl).newInstance();
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    else if (key.endsWith("Size")) {
      return parseSize(value);
    }
    else if (key.endsWith("Width") || key.endsWith("Height")) {
      return getInteger(value, key);
    }
    else if (key.endsWith("grayFilter")) {
      return parseGrayFilter(value);
    }
    else {
      Icon icon = value.startsWith("AllIcons.") ? IconLoader.getReflectiveIcon(value, AllIcons.class.getClassLoader()) : null;      if (icon != null) {
        return new IconUIResource(icon);
      }
      Color color = parseColor(value);
      if (color != null) {
        return new ColorUIResource(color);
      }
      Integer intVal = getInteger(value, null);
      if (intVal != null) {
        return intVal;
      }
    }

    return value;
  }

  public static Object parseValue(String key, @NotNull String value) {
    ClassLoader cl = UIManager.getLookAndFeel().getClass().getClassLoader();
    return parseValue(key, value, cl);
  }

  private static Insets parseInsets(String value) {
    final java.util.List<String> numbers = StringUtil.split(value, ",");
    return new JBInsets(Integer.parseInt(numbers.get(0)),
                        Integer.parseInt(numbers.get(1)),
                        Integer.parseInt(numbers.get(2)),
                        Integer.parseInt(numbers.get(3))).asUIResource();
  }

  private static UIUtil.GrayFilter parseGrayFilter(String value) {
    java.util.List<String> numbers = StringUtil.split(value, ",");
    return new UIUtil.GrayFilter(Integer.parseInt(numbers.get(0)),
                                 Integer.parseInt(numbers.get(1)),
                                 Integer.parseInt(numbers.get(2))).asUIResource();
  }

  @SuppressWarnings("UseJBColor")
  private static Color parseColor(String value) {
    if (value != null) {
      value = StringUtil.trimStart(value, "#");
      if (value.length() == 8) {
        final Color color = ColorUtil.fromHex(value.substring(0, 6));
        try {
          int alpha = Integer.parseInt(value.substring(6, 8), 16);
          return new ColorUIResource(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        }
        catch (Exception ignore) { }
        return null;
      }
    }
    Color color = ColorHexUtil.fromHex(value, null);
    return color == null ? null : new ColorUIResource(color);
  }

  private static Integer getInteger(String value, @Nullable String key) {
    try {
      return Integer.parseInt(StringUtil.trimEnd(value, ".0"));
    }
    catch (NumberFormatException e) {
      if (key != null) {
        LOG.warn(key + " = " + value);
      }
      return null;
    }
  }

  private static Dimension parseSize(String value) {
    final List<String> numbers = StringUtil.split(value, ",");
    return new JBDimension(Integer.parseInt(numbers.get(0)), Integer.parseInt(numbers.get(1))).asUIResource();
  }

  public String getEditorSchemeName() {
    return editorSchemeName;
  }

  public void setEditorSchemeName(String editorSchemeName) {
    this.editorSchemeName = editorSchemeName;
  }

  @Contract("null -> false")
  public static boolean isThemeFile(@Nullable VirtualFile file) {
    return file != null && StringUtil.endsWithIgnoreCase(file.getName(), FILE_EXT_ENDING);
  }

  static class PaletteScope {
    final Map<String, String> newPalette = new HashMap<>();
    final Map<String, Integer> alphas = new HashMap<>();

    private byte[] hash = null;

    byte @NotNull [] digest() {
      if (hash != null) return hash;

      final Hasher hasher = Hashing.sha256().newHasher();
      //order is significant
      for (Map.Entry<String, String> e : new TreeMap<>(newPalette).entrySet()) {
        hasher.putString(e.getKey(), StandardCharsets.UTF_8);
        hasher.putString(e.getValue(), StandardCharsets.UTF_8);
      }
      //order is significant
      for (Map.Entry<String, Integer> e : new TreeMap<>(alphas).entrySet()) {
        hasher.putString(e.getKey(), StandardCharsets.UTF_8);
        final Integer value = e.getValue();
        if (value != null) {
          hasher.putInt(value);
        }
      }
      hash = hasher.hash().asBytes();
      return hash;
    }
  }

  static class PaletteScopeManager {
    final PaletteScope ui = new PaletteScope();
    final PaletteScope checkBoxes = new PaletteScope();
    final PaletteScope radioButtons = new PaletteScope();
    final PaletteScope trees = new PaletteScope();

    PaletteScopeManager() {
    }

    PaletteScope getScope(String colorKey) {
      if (colorKey.startsWith("Checkbox.")) return checkBoxes;
      if (colorKey.startsWith("Radio.")) return radioButtons;
      if (colorKey.startsWith("Tree.iconColor")) return trees;
      if (colorKey.startsWith("Objects.")) return ui;
      if (colorKey.startsWith("Actions.")) return ui;
      if (colorKey.startsWith("#")) return ui;

      LOG.warn("No color scope defined for key: " + colorKey);
      return null;
    }

    @Nullable
    PaletteScope getScopeByURL(@Nullable URL url) {
      if (url != null) {
        String path = url.toString();
        String file = path.substring(path.lastIndexOf('/') + 1);

        if (path.contains("/com/intellij/ide/ui/laf/icons/")) {
          if (file.equals("treeCollapsed.svg") || file.equals("treeExpanded.svg")) return trees;
          if (file.startsWith("check")) return checkBoxes;
          if (file.startsWith("radio")) return checkBoxes; //same set of colors as for checkboxes
          return null;
        }
      }
      return ui;
    }
  }

  //<editor-fold desc="JSON deserialization methods">
  @SuppressWarnings("unused")
  private void setName(String name) {
    this.name = name;
  }

  @SuppressWarnings("unused")
  private void setDark(boolean dark) {
    this.dark = dark;
  }

  @SuppressWarnings("unused")
  private void setAuthor(String author) {
    this.author = author;
  }

  @SuppressWarnings("unused")
  private void setUi(Map<String, Object> ui) {
    this.ui = ui;
  }

  @SuppressWarnings("unused")
  private void setIcons(Map<String, Object> icons) {
    this.icons = icons;
  }

  @SuppressWarnings("unused")
  public void setEditorScheme(String editorScheme) {
    this.editorScheme = editorScheme;
  }

  public void setBackground(Map<String, Object> background) {
    this.background = background;
  }

  public void setEmptyFrameBackground(Map<String, Object> emptyFrameBackground) {
    this.emptyFrameBackground = emptyFrameBackground;
  }

  public Map<String, Object> getColors() {
    return colors;
  }

  public void setColors(Map<String, Object> colors) {
    this.colors = colors;
  }
  //</editor-fold>
}