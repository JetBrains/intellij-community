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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LayeredIcon extends AbstractSizeAdjustingIcon {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LayeredIcon");
  private final Icon[] myIcons;
  private Icon[] myScaledIcons;
  private float myScale = 1f;
  private final boolean[] myDisabledLayers;
  private final int[] myHShifts;
  private final int[] myVShifts;

  private int myXShift;
  private int myYShift;

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

  public static LayeredIcon createHorizontalIcon(@NotNull Icon... icons) {
    LayeredIcon result = new LayeredIcon(icons.length);
    int maxHeight = 0;
    for (Icon icon : icons) {
      maxHeight = Math.max(maxHeight, icon.getIconHeight());
    }
    int hShift = 0;
    for (int i = 0; i < icons.length; i++) {
      result.setIcon(icons[i], i, hShift, (maxHeight - icons[i].getIconHeight()) / 2);
      hShift += icons[i].getIconWidth() + 1;
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LayeredIcon)) return false;

    final LayeredIcon icon = (LayeredIcon)o;

    if (myHeight != icon.myHeight) return false;
    if (myWidth != icon.myWidth) return false;
    if (myXShift != icon.myXShift) return false;
    if (myYShift != icon.myYShift) return false;
    if (!Arrays.equals(myHShifts, icon.myHShifts)) return false;
    if (!Arrays.equals(myIcons, icon.myIcons)) return false;
    if (!Arrays.equals(myVShifts, icon.myVShifts)) return false;
    if (myScale != icon.myScale) return false;

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

  public Icon[] getAllLayers() {
    return myIcons;
  }

  public void setIcon(Icon icon, int layer, int hShift, int vShift) {
    if (icon instanceof LayeredIcon) {
      ((LayeredIcon)icon).checkIHaventIconInsideMe(this);
    }
    myIcons[layer] = icon;
    myHShifts[layer] = hShift;
    myVShifts[layer] = vShift;
    adjustSize();
  }

  public void setIcon(Icon icon, int layer, int constraint) {
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
          "The constraint should be one of SwingConstants [NORTH...NORTH_WEST], actual value is " + constraint);
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
    for (int i = 0; i < myIcons.length; i++) {
      Icon icon = getOrScale(i);
      if (icon == null || myDisabledLayers[i]) continue;
      int xOffset = x + scale(myXShift + myHShifts[i]);
      int yOffset = y + scale(myYShift + myVShifts[i]);
      icon.paintIcon(c, g, xOffset, yOffset);
    }
  }

  private Icon getOrScale(int i) {
    if (myScale == 1f) {
      return myIcons[i];
    }
    if (myScaledIcons == null) {
      myScaledIcons = new Icon[myIcons.length];
    }

    Icon icon = myScaledIcons[i];
    if (icon == null && myIcons[i] != null) {
      icon = myIcons[i];
      if (icon instanceof ScalableIcon) {
        icon = myScaledIcons[i] = ((ScalableIcon)icon).scale(myScale);
      }
    }
    return icon;
  }

  private Icon[] getIcons() {
    return myScaledIcons != null && myScale != 1f ? myScaledIcons : myIcons;
  }

  public boolean isLayerEnabled(int layer) {
    return !myDisabledLayers[layer];
  }

  public void setLayerEnabled(int layer, boolean enabled) {
    myDisabledLayers[layer] = !enabled;
  }

  @Override
  public int getIconWidth() {
    if (myWidth <= 1) { //icon is not loaded yet
      adjustSize();
      return scale(myWidth);
    }
    return scale(super.getIconWidth());
  }

  @Override
  public int getIconHeight() {
    if (myHeight <= 1) { //icon is not loaded yet
      adjustSize();
      return scale(myHeight);
    }
    return scale(super.getIconHeight());
  }

  private int scale(int n) {
    return myScale == 1f ? n : (int)(n * myScale);
  }

  @Override
  protected void adjustSize() {
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    boolean hasNotNullIcons = false;
    for (int i = 0; i < myIcons.length; i++) {
      Icon icon = myIcons[i];
      if (icon == null) continue;
      hasNotNullIcons = true;
      int hShift = myHShifts[i];
      int vShift = myVShifts[i];
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

  @Override
  public Icon scale(float scaleFactor) {
    if (myScale != scaleFactor) {
      myScale = scaleFactor;
      if (myScaledIcons!= null) Arrays.fill(myScaledIcons, null);
      adjustSize();
    }
    return this;
  }
}
