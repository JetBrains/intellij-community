/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.lw;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/**
 * @author yole
 */
public class ColorDescriptor extends Color {
  private Color myColor;
  private String mySwingColor;
  private String mySystemColor;
  private String myAWTColor;

  public static ColorDescriptor fromSwingColor(final String swingColor) {
    ColorDescriptor result = new ColorDescriptor(UIManager.getColor(swingColor));
    result.myColor = null;
    result.mySwingColor = swingColor;
    return result;
  }

  public static ColorDescriptor fromSystemColor(final String systemColor) {
    ColorDescriptor result = new ColorDescriptor(getColorField(SystemColor.class, systemColor));
    result.myColor = null;
    result.mySystemColor = systemColor;
    return result;
  }

  public static ColorDescriptor fromAWTColor(final String awtColor) {
    ColorDescriptor result = new ColorDescriptor(getColorField(Color.class, awtColor));
    result.myColor = null;
    result.myAWTColor = awtColor;
    return result;
  }

  private static Color getColorField(final Class<? extends Color> aClass, final String fieldName) {
    try {
      final Field field = aClass.getDeclaredField(fieldName);
      return (Color)field.get(null);
    }
    catch (NoSuchFieldException e) {
      return Color.BLACK;
    }
    catch (IllegalAccessException e) {
      return Color.BLACK;
    }
  }

  public ColorDescriptor(final Color color) {
    super(color.getRGB());
    myColor = color;
  }

  public Color getResolvedColor() {
    return new Color(getRGB());
  }

  public Color getColor() {
    return myColor;
  }

  public String getSwingColor() {
    return mySwingColor;
  }

  public String getSystemColor() {
    return mySystemColor;
  }

  public String getAWTColor() {
    return myAWTColor;
  }

  public String toString() {
    if (mySwingColor != null) {
      return mySwingColor;
    }
    if (mySystemColor != null) {
      return mySystemColor;
    }
    if (myAWTColor != null) {
      return myAWTColor;
    }
    return "[" + getRed() + "," + getGreen() + "," + getBlue() + "]";
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ColorDescriptor)) {
      return false;
    }
    ColorDescriptor rhs = (ColorDescriptor) obj;
    if (myColor != null) {
      return rhs.myColor != null && rhs.myColor.equals(myColor);
    }
    if (mySwingColor != null) {
      return rhs.mySwingColor != null && rhs.mySwingColor.equals(mySwingColor);
    }
    if (mySystemColor != null) {
      return rhs.mySystemColor != null && rhs.mySystemColor.equals(mySystemColor);
    }
    if (myAWTColor != null) {
      return rhs.myAWTColor != null && rhs.myAWTColor.equals(myAWTColor);
    }
    return false;
  }
}
