package com.intellij.ui.content;

import javax.swing.*;

public class AlertIcon {

  private Icon myIcon;
  private int myVShift;
  private int myHShift;

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
}
