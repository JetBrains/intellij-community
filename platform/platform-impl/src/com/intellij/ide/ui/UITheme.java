// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ui.JBUI.Borders.customLine;
import static com.intellij.util.ui.JBUI.asUIResource;

/**
 * @author Konstantin Bulenkov
 */
public class UITheme {
  public static final String FILE_EXT_ENDING = ".theme.json";
  private String name;
  private boolean dark;
  private String author;
  private String id;
  private String editorScheme;
  private Map<String, Object> ui;
  private Map<String, Object> icons;
  private IconPathPatcher patcher;
  private Map<String, Object> background;
  private ClassLoader providerClassLoader = getClass().getClassLoader();
  private String editorSchemeName;
  private SVGLoader.SvgColorPatcher colorPatcher;

  private UITheme() {
  }

  public String getName() {
    return name;
  }

  public boolean isDark() {
    return dark;
  }

  public String getAuthor() {
    return author;
  }

  public static UITheme loadFromJson(InputStream stream, @NotNull String themeId, @Nullable ClassLoader provider) throws IOException {
    UITheme theme = new ObjectMapper().readValue(stream, UITheme.class);
    theme.id = themeId;
    if (provider != null) {
      theme.providerClassLoader = provider;
    }
    if (theme.icons != null && !theme.icons.isEmpty()) {
      theme.patcher = new IconPathPatcher() {
        @Nullable
        @Override
        public String patchPath(String path, ClassLoader classLoader) {
          if (classLoader instanceof PluginClassLoader) {
            String pluginId = ((PluginClassLoader)classLoader).getPluginId().getIdString();
            Object icons = theme.icons.get(pluginId);
            if (icons instanceof Map) {
              Object pluginIconPath = ((Map)icons).get(path);
              if (pluginIconPath instanceof String) {
                return (String)pluginIconPath;
              }
            }
          }

          Object value = theme.icons.get(path);
          return value instanceof String ? (String)value : null;
        }

        @Nullable
        @Override
        public ClassLoader getContextClassLoader(String path, ClassLoader originalClassLoader) {
          return theme.providerClassLoader;
        }
      };
      Object palette = theme.icons.get("ColorPalette");
      if (palette instanceof Map) {
        Map colors = (Map)palette;
        Map<String, String> newPalette = new HashMap<>();
        Map<String, Integer> alphas = new HashMap<>();
        for (Object o : colors.keySet()) {
          String key = toColorString(o.toString(), theme.isDark());
          Object v = colors.get(o.toString());
          if (v instanceof String) {
            String value = (String)v;
            String alpha = null;
            if (value.length() == 9) {
              alpha = value.substring(7);
              value = value.substring(0, 7);
            }
            if (ColorUtil.fromHex(key, null) != null && ColorUtil.fromHex(value, null) != null) {
              newPalette.put(key, value);
              int fillTransparency = -1;
              if (alpha != null) {
                try {
                  fillTransparency = Integer.parseInt(alpha, 16);
                } catch (Exception ignore) {}
              }
              if (fillTransparency != -1) {
                alphas.put(value, fillTransparency);
              }
            }
          }
        }

        theme.colorPatcher = new SVGLoader.SvgColorPatcher() {
          @Override
          public void patchColors(Element svg) {
            String fill = svg.getAttribute("fill");
            if (fill != null) {
              String newFill = newPalette.get(StringUtil.toLowerCase(fill));
              if (newFill != null) {
                svg.setAttribute("fill", newFill);
                if (alphas.get(newFill) != null) {
                  svg.setAttribute("fill-opacity", String.valueOf((Float.valueOf(alphas.get(newFill)) / 255f)));
                }
              }
            }
            NodeList nodes = svg.getChildNodes();
            int length = nodes.getLength();
            for (int i = 0; i < length; i++) {
              Node item = nodes.item(i);
              if (item instanceof Element) {
                patchColors((Element)item);
              }
            }

          }
        };
      }
    }
    return theme;
  }

  private static String toColorString(String fillValue, boolean darkTheme) {
    if (darkTheme && fillValue.startsWith("Actions.") && !fillValue.endsWith(".Dark")) {
      fillValue += ".Dark";
    }
    String color = colorPalette.get(fillValue);
    if (color != null) {
      return StringUtil.toLowerCase(color);
    }
    return StringUtil.toLowerCase(fillValue);
  }

  private static final Map<String, String> colorPalette = new HashMap<>();
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

  public void applyProperties(UIDefaults defaults) {
    if (ui == null) return;

    for (Map.Entry<String, Object> entry : ui.entrySet()) {
      apply(entry.getKey(), entry.getValue(), defaults);
    }
  }

  public IconPathPatcher getPatcher() {
    return patcher;
  }

  public SVGLoader.SvgColorPatcher getColorPatcher() {
    return colorPatcher;
  }

  @NotNull
  public ClassLoader getProviderClassLoader() {
    return providerClassLoader;
  }

  private static void apply(String key, Object value, UIDefaults defaults) {
    if (value instanceof HashMap) {
      for (Map.Entry<String, Object> o : ((HashMap<String, Object>)value).entrySet()) {
        apply(key + "." + o.getKey(), o.getValue(), defaults);
      }
    } else {
      value = parseValue(key, value.toString());
      if (key.startsWith("*.")) {
        String tail = key.substring(1);
        Object finalValue = value;
        addPattern(key, value, defaults);

        //please DO NOT stream on UIDefaults directly
        ((UIDefaults)defaults.clone()).keySet().stream()
          .filter(k -> k instanceof String && ((String)k).endsWith(tail))
          .forEach(k -> defaults.put(k, finalValue));
      } else {
        defaults.put(key, value);
      }
    }
  }

  private static void addPattern(String key, Object value, UIDefaults defaults) {
    Object o = defaults.get("*");
    if (! (o instanceof Map)) {
      o = new HashMap<String, Object>();
      defaults.put("*", o);
    }
    Map map = (Map)o;
    if (key != null && key.startsWith("*.")) {
      map.put(key.substring(2), value);
    }
  }

  public static Object parseValue(String key, @NotNull String value) {
    if ("null".equals(value)) {
      return null;
    }
    if ("true".equals(value)) return Boolean.TRUE;
    if ("false".equals(value)) return Boolean.FALSE;

    if (key.endsWith("Insets") || key.endsWith("padding")) {
      return parseInsets(value);
    } else if (key.endsWith("Border") || key.endsWith("border")) {
      try {
        List<String> ints = StringUtil.split(value, ",");
        if (ints.size() == 4) {
          return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
        } else if (ints.size() == 5) {
          return asUIResource(customLine(ColorUtil.fromHex(ints.get(4)),
                                         Integer.parseInt(ints.get(0)),
                                         Integer.parseInt(ints.get(1)),
                                         Integer.parseInt(ints.get(2)),
                                         Integer.parseInt(ints.get(3))));
        } else if (ColorUtil.fromHex(value, null) != null) {
          return asUIResource(customLine(ColorUtil.fromHex(value), 1));
        } else {
          return Class.forName(value).newInstance();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else if (key.endsWith("Size")) {
      return parseSize(value);
    } else if (key.endsWith("Width")) {
      return getInteger(value);
    } else if (key.endsWith("grayFilter")) {
      return parseGrayFilter(value);
    } else {
      final Color color = parseColor(value);
      final Integer invVal = getInteger(value);
      Icon icon = value.startsWith("AllIcons.") ? IconLoader.getIcon(value) : null;
      if (color != null) {
        return  new ColorUIResource(color);
      } else if (invVal != null) {
        return invVal;
      } else if (icon != null) {
        return new IconUIResource(icon);
      }
    }
    return value;
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
        } catch (Exception ignore){}
        return null;
      }
    }
    return ColorUtil.fromHex(value, null);
  }

  private static Integer getInteger(String value) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
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

  //
  //json deserialization methods
  //

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
}
