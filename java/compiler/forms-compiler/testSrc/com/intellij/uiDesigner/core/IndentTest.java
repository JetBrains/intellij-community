// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.core;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public class IndentTest extends TestCase {
  public void testSimple() {
    final GridLayoutManager layout = new GridLayoutManager(1,1, new Insets(0,0,0,0), 0, 0);
    final JPanel panel = new JPanel(layout);

    final JTextField field1 = new JTextField();
    field1.setMinimumSize(new Dimension(5,20));
    field1.setPreferredSize(new Dimension(10,20));

    panel.add(field1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1));
    panel.doLayout();

    assertEquals(15, panel.getMinimumSize().width);
    assertEquals(20, panel.getPreferredSize().width);

    panel.setSize(new Dimension(100, 100));
    panel.doLayout();

    assertEquals(10, field1.getBounds().x);
    assertEquals(90, field1.getBounds().width);
  }
}
