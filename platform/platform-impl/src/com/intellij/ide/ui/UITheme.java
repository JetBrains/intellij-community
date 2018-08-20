// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
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

/**
 * @author Konstantin Bulenkov
 */
public class UITheme {
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

  public static UITheme loadFromJson(InputStream stream, @NotNull String themeId, @NotNull ClassLoader provider) throws IOException {
    UITheme theme = new ObjectMapper().readValue(stream, UITheme.class);
    theme.id = themeId;
    theme.providerClassLoader = provider;
    if (!theme.icons.isEmpty()) {
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
        for (Object o : colors.keySet()) {
          String key = toColorString(o.toString());
          Object v = colors.get(o.toString());
          if (v instanceof String) {
            String value = (String)v;
            if (ColorUtil.fromHex(key, null) != null && ColorUtil.fromHex(value, null) != null) {
              newPalette.put(key, value);
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

  private static String toColorString(String fillValue) {
    String color = colorPalette.get(fillValue);
    if (color != null) {
      return StringUtil.toLowerCase(color);
    }
    return StringUtil.toLowerCase(fillValue);
  }

  private static final Map<String, String> colorPalette = new HashMap<>();
  static {
    colorPalette.put("Action.Red", "#DB5860");
    colorPalette.put("Action.Red.Dark", "#C75450");
    colorPalette.put("Action.Yellow", "#EDA200");
    colorPalette.put("Action.Yellow.Dark", "#F0A732");
    colorPalette.put("Action.Green", "#59A869");
    colorPalette.put("Action.Green.Dark", "#499C54");
    colorPalette.put("Action.Blue", "#389FD6");
    colorPalette.put("Action.Blue.Dark", "#3592C4");
    colorPalette.put("Action.Grey", "#6E6E6E");
    colorPalette.put("Action.Grey.Dark", "#AFB1B3");
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

        //please DO NOT invoke forEach on UIDefaults directly
        ((UIDefaults)defaults.clone()).entrySet().forEach(e -> {
          if (e.getKey() instanceof String && ((String)e.getKey()).endsWith(tail)) {
            defaults.put(e.getKey(), finalValue);
          }
        });
      } else {
        defaults.put(key, value);
      }
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
        if (StringUtil.split(value, ",").size() == 4) {
          return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
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
    if (value != null && value.length() == 8) {
      final Color color = ColorUtil.fromHex(value.substring(0, 6));
      try {
        int alpha = Integer.parseInt(value.substring(6, 8), 16);
        return new ColorUIResource(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
      } catch (Exception ignore){}
      return null;
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
