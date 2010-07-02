package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class SizedIcon implements Icon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;

  public SizedIcon(Icon delegate, int width, int height) {
    myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    int dx = myWidth - myDelegate.getIconWidth();
    int dy = myWidth - myDelegate.getIconHeight();
    if (dx > 0 || dy > 0) {
      myDelegate.paintIcon(c, g, x + dx/2, y + dy/2);
    }
    else {
      myDelegate.paintIcon(c, g, x, y);
    }
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }
}
