
/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 *
 */
public class RowIcon implements Icon {

  private Icon[] myIcons;
  private int myWidth;
  private int myHeight;

  public RowIcon(int iconCount/*, int orientation*/) {
    myIcons = new Icon[iconCount];
    //myOrientation = orientation;
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
    recalculateSize();
  }

  public Icon getIcon(int index) {
    return myIcons[index];
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    int _x = x;
    for (Icon icon : myIcons) {
      if (icon == null) continue;
      icon.paintIcon(c, g, _x, y);
      _x += icon.getIconWidth();
      //_y += icon.getIconHeight();
    }
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }

  private void recalculateSize() {
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
}
