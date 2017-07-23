/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LayeredIcon extends JBUI.UpdatingScalableJBIcon<LayeredIcon> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LayeredIcon");
  private final Icon[] myIcons;
  private Icon[] myScaledIcons;
  private final boolean[] myDisabledLayers;
  private final int[] myHShifts;
  private final int[] myVShifts;

  private int myXShift;
  private int myYShift;

  private int myWidth;
  private int myHeight;

  public LayeredIcon(int layerCount) {
    myIcons = new Icon[layerCount];
    myDisabledLayers = new boolean[layerCount];
    myHShifts = new int[layerCount];
    myVShifts = new int[layerCount];
  }

  public LayeredIcon(@NotNull Icon... icons) {
    this(icons.length);
    for (int i = 0; i < icons.length; i++) {
      setIcon(icons[i], i);
    }
  }

  protected LayeredIcon(LayeredIcon icon) {
    super(icon);
    myIcons = ArrayUtil.copyOf(icon.myIcons);
    myScaledIcons = null;
    myDisabledLayers = ArrayUtil.copyOf(icon.myDisabledLayers);
    myHShifts = ArrayUtil.copyOf(icon.myHShifts);
    myVShifts = ArrayUtil.copyOf(icon.myVShifts);
    myXShift = icon.myXShift;
    myYShift = icon.myYShift;
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
  }

  @NotNull
  @Override
  protected LayeredIcon copy() {
    return new LayeredIcon(this);
  }

  @NotNull
  private Icon[] myScaledIcons() {
    if (myScaledIcons != null) {
      return myScaledIcons;
    }
    if (getScale() == 1f) {
      return myScaledIcons = myIcons;
    }
    for (Icon icon : myIcons) {
      if (icon != null && !(icon instanceof ScalableIcon)) {
        return myScaledIcons = myIcons;
      }
    }
    myScaledIcons = new Icon[myIcons.length];
    for (int i = 0; i < myIcons.length; i++) {
      if (myIcons[i] != null) {
        myScaledIcons[i] = ((ScalableIcon)myIcons[i]).scale(getScale());
      }
    }
    return myScaledIcons;
  }

  @Override
  public LayeredIcon withJBUIPreScaled(boolean preScaled) {
    super.withJBUIPreScaled(preScaled);
    updateSize();
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LayeredIcon)) return false;
    if (!super.equals(o)) return false;

    final LayeredIcon icon = (LayeredIcon)o;

    if (myHeight != icon.myHeight) return false;
    if (myWidth != icon.myWidth) return false;
    if (myXShift != icon.myXShift) return false;
    if (myYShift != icon.myYShift) return false;
    if (!Arrays.equals(myHShifts, icon.myHShifts)) return false;
    if (!Arrays.equals(myIcons, icon.myIcons)) return false;
    if (!Arrays.equals(myVShifts, icon.myVShifts)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public void setIcon(Icon icon, int layer) {
    setIcon(icon, layer, 0, 0);
  }

  public Icon getIcon(int layer) {
    return myIcons[layer];
  }

  @NotNull
  public Icon[] getAllLayers() {
    return myIcons;
  }

  public void setIcon(Icon icon, int layer, int hShift, int vShift) {
    if (icon instanceof LayeredIcon) {
      ((LayeredIcon)icon).checkIHaventIconInsideMe(this);
    }
    myIcons[layer] = icon;
    myScaledIcons = null;
    myHShifts[layer] = hShift;
    myVShifts[layer] = vShift;
    updateSize();
  }

  /**
   *
   * @param constraint is expected to be one of compass-directions or CENTER
   */
  public void setIcon(Icon icon, int layer, @MagicConstant(valuesFromClass = SwingConstants.class) int constraint) {
    int width = getIconWidth();
    int height = getIconHeight();
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (width <= 1 || height <= 1) {
      setIcon(icon, layer);
      return;
    }
    int x;
    int y;
    switch (constraint) {
      case SwingConstants.CENTER:
        x = (width - w) / 2;
        y = (height - h) /2;
        break;
      case SwingConstants.NORTH:
        x = (width - w) / 2;
        y = 0;
        break;
      case SwingConstants.NORTH_EAST:
        x = width - w;
        y = 0;
        break;
      case SwingConstants.EAST:
        x = width - w;
        y = (height - h) / 2;
        break;
      case SwingConstants.SOUTH_EAST:
        x = width - w;
        y = height - h;
        break;
      case SwingConstants.SOUTH:
        x = (width - w) / 2;
        y = height - h;
        break;
      case SwingConstants.SOUTH_WEST:
        x = 0;
        y = height - h;
        break;
      case SwingConstants.WEST:
        x = 0;
        y = (height - h) / 2;
        break;
      case SwingConstants.NORTH_WEST:
        x = 0;
        y = 0;
        break;
      default:
        throw new IllegalArgumentException(
          "The constraint should be one of SwingConstants' compass-directions [1..8] or CENTER [0], actual value is " + constraint);
    }
    setIcon(icon, layer, x, y);
  }

  private void checkIHaventIconInsideMe(Icon icon) {
    LOG.assertTrue(icon != this);
    for (Icon child : myIcons) {
      if (child instanceof LayeredIcon) ((LayeredIcon)child).checkIHaventIconInsideMe(icon);
    }
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (updateJBUIScale()) updateSize();
    Icon[] icons = myScaledIcons();
    for (int i = 0; i < icons.length; i++) {
      Icon icon = icons[i];
      if (icon == null || myDisabledLayers[i]) continue;
      int xOffset = x + scaleVal(myXShift + myHShifts(i), Scale.INSTANCE);
      int yOffset = y + scaleVal(myYShift + myVShifts(i), Scale.INSTANCE);
      icon.paintIcon(c, g, xOffset, yOffset);
    }
  }

  public boolean isLayerEnabled(int layer) {
    return !myDisabledLayers[layer];
  }

  public void setLayerEnabled(int layer, boolean enabled) {
    myDisabledLayers[layer] = !enabled;
  }

  @Override
  public int getIconWidth() {
    if (myWidth <= 1 || updateJBUIScale()) {
      updateSize();
    }
    return scaleVal(myWidth, Scale.INSTANCE);
  }

  @Override
  public int getIconHeight() {
    if (myHeight <= 1 || updateJBUIScale()) {
      updateSize();
    }
    return scaleVal(myHeight, Scale.INSTANCE);
  }

  private int myHShifts(int i) {
    return scaleVal(myHShifts[i], Scale.JBUI);
  }

  private int myVShifts(int i) {
    return scaleVal(myVShifts[i], Scale.JBUI);
  }

  protected void updateSize() {
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    boolean hasNotNullIcons = false;
    for (int i = 0; i < myIcons.length; i++) {
      Icon icon = myIcons[i];
      if (icon == null) continue;
      hasNotNullIcons = true;
      int hShift = myHShifts(i);
      int vShift = myVShifts(i);
      minX = Math.min(minX, hShift);
      maxX = Math.max(maxX, hShift + icon.getIconWidth());
      minY = Math.min(minY, vShift);
      maxY = Math.max(maxY, vShift + icon.getIconHeight());
    }
    if (!hasNotNullIcons) return;
    myWidth = maxX - minX;
    myHeight = maxY - minY;

    if (myIcons.length > 1) {
      myXShift = -minX;
      myYShift = -minY;
    }
  }

  public static Icon create(final Icon backgroundIcon, final Icon foregroundIcon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(backgroundIcon, 0);
    layeredIcon.setIcon(foregroundIcon, 1);
    return layeredIcon;
  }

  @Override
  public String toString() {
    return "Layered icon. myIcons=" + Arrays.asList(myIcons);
  }
}
