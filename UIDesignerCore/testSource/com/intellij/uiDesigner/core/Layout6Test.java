package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public final class Layout6Test extends TestCase{
  

  /**
   * control(min size 10) control(same) control(same)
   */ 
  public void test1() {
    final GridLayoutManager layoutManager = new GridLayoutManager(2,3, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layoutManager);

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(10,30));
    
    final JTextField field2 = new JTextField();
    field2.setPreferredSize(new Dimension(10,30));

    final JTextField field3 = new JTextField();
    field3.setPreferredSize(new Dimension(10,30));
    
    panel.add(field1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    panel.add(field2, new GridConstraints(0,1,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    panel.add(field3, new GridConstraints(0,2,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null, 0));

    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(30, preferredSize.width);
    
    for (int size = 31; size < 100; size++) {
      panel.setSize(size, 30);
      layoutManager.invalidateLayout(panel); // wisdom
      panel.doLayout();
    }
  }

}
