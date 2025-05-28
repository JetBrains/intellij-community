// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.uiDesigner.lw;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

public final class ColorDescriptor {
  private final Color myColor;
  private final String mySwingColor;
  private final String mySystemColor;
  private final String myAWTColor;

  public ColorDescriptor(final Color color) {
    this(color, null, null, null);
  }

  private ColorDescriptor(Color color, String swingColor, String systemColor, String AWTColor) {
    myColor = color;
    mySwingColor = swingColor;
    mySystemColor = systemColor;
    myAWTColor = AWTColor;
  }

  static ColorDescriptor fromSwingColor(final String swingColor) {
    return new ColorDescriptor(null, swingColor, null, null);
  }

  static ColorDescriptor fromSystemColor(final String systemColor) {
    return new ColorDescriptor(null, null, systemColor, null);
  }

  static ColorDescriptor fromAWTColor(final String awtColor) {
    return new ColorDescriptor(null, null, null, awtColor);
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

  @Override
  public int hashCode() {
    if (myColor != null) {
      return myColor.hashCode();
    }
    if (mySwingColor != null) {
      return mySwingColor.hashCode();
    }
    if (mySystemColor != null) {
      return mySystemColor.hashCode();
    }
    if (myAWTColor != null) {
      return myAWTColor.hashCode();
    }
    return super.hashCode();
  }

  public boolean isColorSet() {
    return myColor != null || mySwingColor != null || mySystemColor != null || myAWTColor != null;
  }
}
