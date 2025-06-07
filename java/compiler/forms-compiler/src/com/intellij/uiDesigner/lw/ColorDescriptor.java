// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

public final class ColorDescriptor {
  private final Color myColor;
  private String mySwingColor;
  private String mySystemColor;
  private String myAWTColor;

  public ColorDescriptor(final Color color) {
    myColor = color;
  }

  static ColorDescriptor fromSwingColor(final String swingColor) {
    ColorDescriptor result = new ColorDescriptor(null);
    result.mySwingColor = swingColor;
    return result;
  }

  static ColorDescriptor fromSystemColor(final String systemColor) {
    ColorDescriptor result = new ColorDescriptor(null);
    result.mySystemColor = systemColor;
    return result;
  }

  static ColorDescriptor fromAWTColor(final String awtColor) {
    ColorDescriptor result = new ColorDescriptor(null);
    result.myAWTColor = awtColor;
    return result;
  }

  private static Color getColorField(final Class aClass, final String fieldName) {
    try {
      final Field field = aClass.getDeclaredField(fieldName);
      return (Color)field.get(null);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      return Color.black;
    }
  }

  public Color getResolvedColor() {
    if (myColor != null) {
      return myColor;
    }
    if (mySwingColor != null) {
      return UIManager.getColor(mySwingColor);
    }
    if (mySystemColor != null) {
      return getColorField(SystemColor.class, mySystemColor);
    }
    if (myAWTColor != null) {
      return getColorField(Color.class, myAWTColor);
    }
    return null;
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

  @Override
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
    if (myColor != null) {
      return "[" + myColor.getRed() + "," + myColor.getGreen() + "," + myColor.getBlue() + "]";
    }
    return "null";
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ColorDescriptor)) {
      return false;
    }
    ColorDescriptor rhs = (ColorDescriptor) obj;
    if (myColor != null) {
      return myColor.equals(rhs.myColor);
    }
    if (mySwingColor != null) {
      return mySwingColor.equals(rhs.mySwingColor);
    }
    if (mySystemColor != null) {
      return mySystemColor.equals(rhs.mySystemColor);
    }
    if (myAWTColor != null) {
      return myAWTColor.equals(rhs.myAWTColor);
    }
    return false;
  }

  public boolean isColorSet() {
    return myColor != null || mySwingColor != null || mySystemColor != null || myAWTColor != null;
  }
}
