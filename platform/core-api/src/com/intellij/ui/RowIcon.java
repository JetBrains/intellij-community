// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI.CachingScalableJBIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ui.JBUI.ScaleType.OBJ_SCALE;
import static java.lang.Math.ceil;

public class RowIcon extends CachingScalableJBIcon<RowIcon> {
  private final Alignment myAlignment;

  private int myWidth;
  private int myHeight;

  public enum Alignment {TOP, CENTER, BOTTOM}

  private final Icon[] myIcons;
  private Icon[] myScaledIcons;

  {
    getScaleContext().addUpdateListener(() -> updateSize());
    setAutoUpdateScaleContext(false);
  }

  public RowIcon(int iconCount/*, int orientation*/) {
    this(iconCount, Alignment.TOP);
  }

  public RowIcon(int iconCount, Alignment alignment) {
    myAlignment = alignment;
    myIcons = new Icon[iconCount];
    //myOrientation = orientation;
  }

  public RowIcon(Icon... icons) {
    this(icons.length);
    System.arraycopy(icons, 0, myIcons, 0, icons.length);
    updateSize();
  }

  protected RowIcon(RowIcon icon) {
    super(icon);
    myAlignment = icon.myAlignment;
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
    myIcons = ArrayUtil.copyOf(icon.myIcons);
    myScaledIcons = null;
  }

  @NotNull
  @Override
  public RowIcon copy() {
    return new RowIcon(this);
  }

  @NotNull
  private Icon[] myScaledIcons() {
    if (myScaledIcons != null) {
      return myScaledIcons;
    }
    return myScaledIcons = scaleIcons(myIcons, getScale());
  }

  static Icon[] scaleIcons(Icon[] icons, float scale) {
    if (scale == 1f) return icons;
    Icon[] scaledIcons = new Icon[icons.length];
    for (int i = 0; i < icons.length; i++) {
      if (icons[i] != null) {
        scaledIcons[i] = IconUtil.scale(icons[i], null, scale);
      }
    }
    return scaledIcons;
  }

  @NotNull
  public Icon[] getAllIcons() {
    List<Icon> icons = ContainerUtil.packNullables(myIcons);
    return icons.toArray(new Icon[0]);
  }

  public int hashCode() {
    return myIcons.length > 0 ? myIcons[0].hashCode() : 0;
  }

  public boolean equals(Object obj) {
    return obj instanceof RowIcon && Arrays.equals(((RowIcon)obj).myIcons, myIcons);
  }

  public int getIconCount() {
    return myIcons.length;
  }

  public void setIcon(Icon icon, int layer) {
    myIcons[layer] = icon;
    myScaledIcons = null;
    updateSize();
  }

  public Icon getIcon(int index) {
    return myIcons[index];
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    getScaleContext().update();
    int _x = x;
    int _y = y;
    for (Icon icon : myScaledIcons()) {
      if (icon == null) continue;
      switch (myAlignment) {
        case TOP: _y = y;
          break;
        case CENTER: _y = y + (myHeight - icon.getIconHeight())/2;
          break;
        case BOTTOM: _y = y + (myHeight - icon.getIconHeight());
          break;
      }
      icon.paintIcon(c, g, _x, _y);
      _x += icon.getIconWidth();
      //_y += icon.getIconHeight();
    }
  }

  @Override
  public int getIconWidth() {
    getScaleContext().update();
    return (int)ceil(scaleVal(myWidth, OBJ_SCALE));
  }

  @Override
  public int getIconHeight() {
    getScaleContext().update();
    return (int)ceil(scaleVal(myHeight, OBJ_SCALE));
  }

  private void updateSize() {
    int width = 0;
    int height = 0;
    for (Icon icon : myIcons) {
      if (icon == null) continue;
      width += icon.getIconWidth();
      //height += icon.getIconHeight();
      height = Math.max(height, icon.getIconHeight());
    }
    myWidth = width;
    myHeight = height;
  }

  @Override
  public String toString() {
    return "Row icon. myIcons=" + Arrays.asList(myIcons);
  }
}
