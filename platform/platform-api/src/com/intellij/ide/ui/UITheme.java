// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.IconUIResource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class UITheme {
  private Map<String, Object> myProperties;
  private final String myName;
  private final boolean myDark;

  private UITheme(String name, boolean dark) {
    myName = name;
    myDark = dark;
  }

  public String getName() {
    return myName;
  }

  public boolean isDark() {
    return myDark;
  }

  public static UITheme loadFromJson(InputStream stream) throws IOException {
    JSONObject json = new JSONObject(new JSONTokener(stream));
    String name = json.getString("name");
    boolean dark = json.getBoolean("dark");
    UITheme theme = new UITheme(name, dark);
    theme.myProperties = json.getJSONObject("UI").toMap();

    return theme;
  }

  public void applyProperties(UIDefaults defaults) {
    //todo[kb] apply properties to LaF
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
  //  try (FileInputStream stream = new FileInputStream("/Users/kb/Library/Preferences/IntelliJIdea2018.2/scratches/scratch.json")) {
  //    loadFromJson(stream);
  //  }
  //}
}
