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

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class RowIcon extends AbstractSizeAdjustingIcon {
  private final Alignment myAlignment;
  private float myScale = 1f;

  public enum Alignment {TOP, CENTER, BOTTOM}

  private final Icon[] myIcons;
  private Icon[] myScaledIcons;

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
    adjustSize();
  }


  @Override
  public Icon scale(float scale) {
    if (myScale != scale || (myScale != 1f && myScaledIcons == null)) {
      myScale = scale;
      rescale();
    }
    return this;
  }

  private void rescale() {
    if (myScale == 1f) {
      myScaledIcons = null;
      return;
    }

    for (Icon icon : myIcons) {
      if (icon != null && !(icon instanceof ScalableIcon)) {
        return;
      }
    }

    myScaledIcons = new Icon[myIcons.length];
    for (int i = 0; i < myIcons.length; i++) {
      ScalableIcon icon = (ScalableIcon)myIcons[i];
      myScaledIcons[i] = icon == null ? null : icon.scale(myScale);
    }
    adjustSize();
  }

  @TestOnly
  @NotNull
  Icon[] getAllIcons() {
    List<Icon> icons = ContainerUtil.packNullables(myIcons);
    return icons.toArray(new Icon[icons.size()]);
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
    rescale();
    adjustSize();
  }

  public Icon getIcon(int index) {
    return myIcons[index];
  }

  public Icon[] getIcons() {
    Icon[] icons = myScale == 1f ? myIcons : myScaledIcons;
    return icons == null ? myIcons : icons;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int _x = x;
    int _y = y;
    for (Icon icon : getIcons()) {
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
  protected void adjustSize() {
    int width = 0;
    int height = 0;
    for (Icon icon : getIcons()) {
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
