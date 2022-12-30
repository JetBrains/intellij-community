// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBCachingScalableIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RowIcon extends JBCachingScalableIcon<RowIcon> implements com.intellij.ui.icons.RowIcon, IconWithToolTip {
  private final Alignment myAlignment;

  private int myWidth;
  private int myHeight;

  private final Icon @NotNull [] myIcons;
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
  }

  public RowIcon(Icon... icons) {
    this(icons.length);
    System.arraycopy(icons, 0, myIcons, 0, icons.length);
    updateSize();
  }

  protected RowIcon(@NotNull RowIcon icon) {
    super(icon);

    myAlignment = icon.myAlignment;
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
    myIcons = icon.myIcons.clone();
    myScaledIcons = null;
  }

  @NotNull
  @Override
  public RowIcon replaceBy(@NotNull IconReplacer replacer) {
    RowIcon icon = new RowIcon(this);
    for (int i = 0; i < icon.myIcons.length; i++) {
      icon.myIcons[i] = replacer.replaceIcon(icon.myIcons[i]);
    }
    return icon;
  }

  @Override
  public @NotNull RowIcon copy() {
    return new RowIcon(this);
  }

  @Override
  public @NotNull com.intellij.ui.icons.RowIcon deepCopy() {
    RowIcon icon = new RowIcon(this);
    for (int i = 0; i < icon.myIcons.length; i++) {
      icon.myIcons[i] = icon.myIcons[i] == null ? null : IconUtil.copy(icon.myIcons[i], null);
    }
    return icon;
  }

  private Icon @NotNull [] myScaledIcons() {
    if (myScaledIcons != null) {
      return myScaledIcons;
    }
    return myScaledIcons = scaleIcons(myIcons, getScale());
  }

  static Icon @NotNull [] scaleIcons(Icon @NotNull [] icons, float scale) {
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
  public Icon @NotNull [] getAllIcons() {
    List<Icon> list = new ArrayList<>(myIcons.length);
    for (Icon element : myIcons) {
      if (element != null) {
        list.add(element);
      }
    }
    return list.toArray(new Icon[0]);
  }

  public int hashCode() {
    return myIcons.length > 0 ? myIcons[0].hashCode() : 0;
  }

  public boolean equals(Object obj) {
    return obj == this || (obj instanceof RowIcon && Arrays.equals(((RowIcon)obj).myIcons, myIcons));
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
      _y = switch (myAlignment) {
        case TOP -> y;
        case CENTER -> y + (myHeight - icon.getIconHeight()) / 2;
        case BOTTOM -> y + (myHeight - icon.getIconHeight());
      };
      icon.paintIcon(c, g, _x, _y);
      _x += icon.getIconWidth();
      //_y += icon.getIconHeight();
    }
  }

  @Override
  public int getIconWidth() {
    getScaleContext().update();
    return (int)Math.ceil(scaleVal(myWidth, ScaleType.OBJ_SCALE));
  }

  @Override
  public int getIconHeight() {
    getScaleContext().update();
    return (int)Math.ceil(scaleVal(myHeight, ScaleType.OBJ_SCALE));
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
  public @NotNull Icon getDarkIcon(boolean isDark) {
    RowIcon newIcon = copy();
    for (int i=0; i<newIcon.myIcons.length; i++) {
      newIcon.myIcons[i] = newIcon.myIcons[i] == null ? null : IconLoader.getDarkIcon(newIcon.myIcons[i], isDark);
    }
    return newIcon;
  }

  @Override
  public String toString() {
    return "Row icon. myIcons=" + Arrays.asList(myIcons);
  }

  @Override
  public String getToolTip(boolean composite) {
    return LayeredIcon.combineIconTooltips(myIcons);
  }
}
