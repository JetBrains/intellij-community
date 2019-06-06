// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBCachingScalableIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static java.lang.Math.ceil;

public class RowIcon extends JBCachingScalableIcon<RowIcon> implements com.intellij.ui.icons.RowIcon {
  private final com.intellij.ui.icons.RowIcon.Alignment myAlignment;

  private int myWidth;
  private int myHeight;

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public enum Alignment {TOP, CENTER, BOTTOM}

  private final Icon[] myIcons;
  private Icon[] myScaledIcons;

  {
    getScaleContext().addUpdateListener(() -> updateSize());
    setAutoUpdateScaleContext(false);
  }

  public RowIcon(int iconCount/*, int orientation*/) {
    this(iconCount, com.intellij.ui.icons.RowIcon.Alignment.TOP);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public RowIcon(int iconCount, Alignment alignment) {
    com.intellij.ui.icons.RowIcon.Alignment a = null;
    if (alignment == Alignment.TOP) {
      a = com.intellij.ui.icons.RowIcon.Alignment.TOP;
    }
    else if (alignment == Alignment.BOTTOM) {
      a = com.intellij.ui.icons.RowIcon.Alignment.BOTTOM;
    }
    else if (alignment == Alignment.CENTER) {
      a = com.intellij.ui.icons.RowIcon.Alignment.CENTER;
    }
    myAlignment = a;
    myIcons = new Icon[iconCount];
  }

  public RowIcon(int iconCount, com.intellij.ui.icons.RowIcon.Alignment alignment) {
    myAlignment = alignment;
    myIcons = new Icon[iconCount];
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
  @Override
  public com.intellij.ui.icons.RowIcon deepCopy() {
    RowIcon icon = new RowIcon(this);
    for (int i = 0; i < icon.myIcons.length; i++) {
      icon.myIcons[i] = IconUtil.copy(icon.myIcons[i], null);
    }
    return icon;
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

  @Override
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

  @Override
  public int getIconCount() {
    return myIcons.length;
  }

  @Override
  public void setIcon(Icon icon, int layer) {
    myIcons[layer] = icon;
    myScaledIcons = null;
    updateSize();
  }

  @Override
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
  public Icon getDarkIcon(boolean isDark) {
    RowIcon newIcon = copy();
    for (int i=0; i<newIcon.myIcons.length; i++) {
      newIcon.myIcons[i] = IconLoader.getDarkIcon(newIcon.myIcons[i], isDark);
    }
    return newIcon;
  }

  @Override
  public String toString() {
    return "Row icon. myIcons=" + Arrays.asList(myIcons);
  }
}
