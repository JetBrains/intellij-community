package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class EmptyPanelTest extends TestCase{
  
  public void test1() {
    final GridLayoutManager layoutManager = new GridLayoutManager(2,3, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layoutManager);

    assertEquals(0, panel.getPreferredSize().width);
    assertEquals(0, panel.getPreferredSize().height);

    panel.setSize(90, 200);
    panel.doLayout(); // should not crash with exception

    assertTrue(Arrays.equals(new int[]{0,30,60}, layoutManager.getXs()));
    assertTrue(Arrays.equals(new int[]{30,30,30}, layoutManager.getWidths()));

    assertTrue(Arrays.equals(new int[]{0,100}, layoutManager.getYs()));
    assertTrue(Arrays.equals(new int[]{100,100}, layoutManager.getHeights()));

    // add component 
    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(100,20));
    panel.add(button, new GridConstraints(0,1,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,GridConstraints.SIZEPOLICY_CAN_GROW,GridConstraints.SIZEPOLICY_FIXED,null,null,null,
                                          0));
    
    // wisdom
    layoutManager.invalidateLayout(panel);
    
    assertEquals(new Dimension(100,20), panel.getPreferredSize());
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
    assertEquals(100, button.getWidth());
    assertEquals(20, panel.getHeight());
    
    assertTrue(Arrays.equals(new int[]{0,0,100}, layoutManager.getXs()));
    assertTrue(Arrays.equals(new int[]{0,100,0}, layoutManager.getWidths()));
    
    assertTrue(Arrays.equals(new int[]{0,20}, layoutManager.getYs()));
    assertTrue(Arrays.equals(new int[]{20,0}, layoutManager.getHeights()));
  }
}
