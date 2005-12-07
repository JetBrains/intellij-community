package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class Layout3Test extends TestCase{
  public void test1() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,1, new Insets(0,0,0,0), 0, 0));

    final JButton button1 = new JButton();
    button1.setPreferredSize(new Dimension(100,20));
    final JButton button2 = new JButton();
    button2.setPreferredSize(new Dimension(100,100));

    panel.add(button1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    panel.add(button2, new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(120, preferredSize.height);
  }
}
