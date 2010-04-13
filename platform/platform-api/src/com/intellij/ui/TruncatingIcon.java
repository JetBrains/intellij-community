package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class TruncatingIcon implements Icon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;

  public TruncatingIcon(Icon delegate, int width, int height) {
    myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    myDelegate.paintIcon(c, g, x, y);
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }
}
