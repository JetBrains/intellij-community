/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LayeredIcon implements Icon {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LayeredIcon");
  private final Icon[] myIcons;
  private final boolean[] myDisabledLayers;
  private final int[] myHShifts;
  private final int[] myVShifts;

  private int myWidth;
  private int myHeight;
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
      hShift +=icons[i].getIconWidth() + 1;
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
    recalculateSize();
  }

  private void checkIHaventIconInsideMe(Icon icon) {
    LOG.assertTrue(icon != this);
    for (Icon child : myIcons) {
      if (child instanceof LayeredIcon) ((LayeredIcon)child).checkIHaventIconInsideMe(icon);
    }
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    for(int i = 0; i < myIcons.length; i++){
      Icon icon = myIcons[i];
      if (icon == null || myDisabledLayers[i]) continue;
      icon.paintIcon(c, g, myXShift + x + myHShifts[i], myYShift + y + myVShifts[i]);
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
    if (myWidth <= 1) { //icon is not loaded yet
      recalculateSize();
    }
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    if (myHeight <= 1) { //icon is not loaded yet
      recalculateSize();
    }
    return myHeight;
  }

  private void recalculateSize() {
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    for(int i = 0; i < myIcons.length; i++){
      Icon icon = myIcons[i];
      if (icon == null) continue;
      int hShift = myHShifts[i];
      int vShift = myVShifts[i];
      minX = Math.min(minX, hShift);
      maxX = Math.max(maxX, hShift + icon.getIconWidth());
      minY = Math.min(minY, vShift);
      maxY = Math.max(maxY, vShift + icon.getIconHeight());
    }
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
}
