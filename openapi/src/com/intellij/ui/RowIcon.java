
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
