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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class RowIcon extends JBUI.UpdatingScalableJBIcon<RowIcon> {
  private final Alignment myAlignment;

  private int myWidth;
  private int myHeight;

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
  protected RowIcon copy() {
    return new RowIcon(this);
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
    myScaledIcons = null;
    updateSize();
  }

  public Icon getIcon(int index) {
    return myIcons[index];
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (updateJBUIScale()) updateSize();
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
    if (updateJBUIScale()) updateSize();
    return scaleVal(myWidth, Scale.INSTANCE);
  }

  @Override
  public int getIconHeight() {
    if (updateJBUIScale()) updateSize();
    return scaleVal(myHeight, Scale.INSTANCE);
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
