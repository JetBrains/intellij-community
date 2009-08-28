package com.intellij.ui.content;

import javax.swing.*;
import java.awt.*;

public class AlertIcon implements Icon {

  private Icon myIcon;
  private int myVShift;
  private int myHShift;

  public AlertIcon(final Icon icon) {
    this(icon, 0, 0);
  }

  public AlertIcon(final Icon icon, final int VShift, final int HShift) {
    myIcon = icon;
    myVShift = VShift;
    myHShift = HShift;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public int getVShift() {
    return myVShift;
  }

  public int getHShift() {
    return myHShift;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    myIcon.paintIcon(c, g, x + myHShift, y + myVShift);
  }

  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myIcon.getIconHeight();
  }
}
