package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class Layout7Test extends TestCase{
  /**
   * label | text field
   *    scroll pane
   */
  public void test1() {
    final GridLayoutManager layoutManager = new GridLayoutManager(2,2, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layoutManager);

    final JLabel label = new JLabel();
    label.setPreferredSize(new Dimension(50,10));
    
    final JTextField field = new JTextField();
    field.setPreferredSize(new Dimension(100,10));

    final JTextField scroll = new JTextField();
    scroll.setPreferredSize(new Dimension(503, 10));

    panel.add(label, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    panel.add(field, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    panel.add(scroll, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    assertEquals(503, panel.getMinimumSize().width);
    assertEquals(503, panel.getPreferredSize().width);

    panel.setSize(503, 100);
    panel.doLayout();

    assertEquals(50, label.getWidth());
    assertEquals(453, field.getWidth());
  }

}
