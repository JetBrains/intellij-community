/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

public class LayeredIcon implements Icon {
  private final Icon[] myIcons;
  private final int[] myHShifts;
  private final int[] myVShifts;

  private int myWidth;
  private int myHeight;
  private int myXShift;
  private int myYShift;

  public LayeredIcon(int layerCount) {
    myIcons = new Icon[layerCount];
    myHShifts = new int[layerCount];
    myVShifts = new int[layerCount];
  }

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

  public int hashCode() {
    return 0;
  }

  public void setIcon(Icon icon, int layer) {
    setIcon(icon, layer, 0, 0);
  }

  public void setIcon(Icon icon, int layer, int hShift, int vShift) {
    myIcons[layer] = icon;
    myHShifts[layer] = hShift;
    myVShifts[layer] = vShift;
    recalculateSize();
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    for(int i = 0; i < myIcons.length; i++){
      Icon icon = myIcons[i];
      if (icon == null) continue;
      icon.paintIcon(c, g, myXShift + x + myHShifts[i], myYShift + y + myVShifts[i]);
    }
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
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
    myXShift = -minX;
    myYShift = -minY;
  }


  public Icon[] getIcons() {
    return myIcons;
  }

  public LayeredIcon replaceFirstIcon(Icon icon) {
    LayeredIcon result = new LayeredIcon(myIcons.length);
    System.arraycopy(myIcons, 0, result.myIcons, 0, myIcons.length);
    System.arraycopy(myHShifts, 0, result.myHShifts, 0, myHShifts.length);
    System.arraycopy(myVShifts, 0, result.myVShifts, 0, myVShifts.length);
    result.myIcons[0] = icon;
    result.recalculateSize();
    return result;
  }
}
