// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

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
  private Map<String, Object> myProperties;
  private String name;
  private boolean dark;
  private String author;
  private Map<String, Object> ui;
  private Map<String, Object> icons;

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

  public static UITheme loadFromJson(InputStream stream) throws IOException {
    return new ObjectMapper().readValue(stream, UITheme.class);
  }

  public void applyProperties(UIDefaults defaults) {
    if (ui == null) return;

    for (Map.Entry<String, Object> entry : ui.entrySet()) {
      apply(entry.getKey(), entry.getValue(), defaults);
    }
  }

  private static void apply(String key, Object value, UIDefaults defaults) {
    if (value instanceof HashMap) {
      for (Map.Entry<String, Object> o : ((HashMap<String, Object>)value).entrySet()) {
        apply(key + "." + o.getKey(), o.getValue(), defaults);
      }
    } else {
      if (value instanceof String) {
        value = parseString(key, (String)value);
      }
      if (key.startsWith("*.")) {
        //todo[kb] apply for all properties
      } else {
        defaults.put(key, value);
      }
    }
  }

  private static Object parseString(String key, String value) {
    //todo[kb] merge with parsing properties file in DarculaLaf
    if (value.startsWith("#")) {
      return ColorUtil.fromHex(value);
    }
    return value;
  }

  public void removeProperties(UIDefaults defaults) {

  }

  public static Object parseValue(String key, @NotNull String value) {
    if ("null".equals(value)) {
      return null;
    }

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
    } else {
      final Color color = parseColor(value);
      final Integer invVal = getInteger(value);
      final Boolean boolVal = "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : null;
      Icon icon = value.startsWith("AllIcons.") ? IconLoader.getIcon(value) : null;
      if (color != null) {
        return  new ColorUIResource(color);
      } else if (invVal != null) {
        return invVal;
      } else if (icon != null) {
        return new IconUIResource(icon);
      } else if (boolVal != null) {
        return boolVal;
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


  //public static void main(String[] args) throws IOException {
  //  try (FileInputStream stream = new FileInputStream("C:\\IDEA\\community\\platform\\platform-api\\src\\com\\intellij\\ide\\ui\\example.theme.json")) {
  //    loadFromJson(stream);
  //  }
  //}

  //json deserialization methods

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
}
