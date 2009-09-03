package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class Layout4Test extends TestCase{
  
  
  public void test1() {
    final JPanel panel = new JPanel(new GridLayoutManager(2,2, new Insets(0,0,0,0), 0, 0));

    // button 1  button 3
    // button 2  button 3
    
    final JButton button1 = new JButton();
    button1.setPreferredSize(new Dimension(100,10));
    final JButton button2 = new JButton();
    button2.setPreferredSize(new Dimension(100,10));
    final JButton button3 = new JButton();
    button3.setPreferredSize(new Dimension(100,200));

    panel.add(button1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null,
      0));

    panel.add(button2, new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null,
      0));

    panel.add(button3, new GridConstraints(0,1,2,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null,
      0));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(200, preferredSize.height);
  }
}
