// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CommandLinePanel extends JPanel {

  private final List<JComponent> myComponents;
  private int myLastWidth;

  public CommandLinePanel(List<JComponent> components) {
    super();
    myComponents = components;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setMinimumSize(new Dimension(500, 100));
    buildRows();
  }

  @Override
  public void doLayout() {
    buildRows();
    super.doLayout();
  }

  public void markForRebuild() {
    myLastWidth = -1;
    invalidate();
  }

  private void buildRows() {
    int parentWidth = Math.max(getWidth(), getMinimumSize().width);
    if (myLastWidth == parentWidth) return;
    myLastWidth = parentWidth;
    removeAll();
    JPanel row = new JPanel(new GridBagLayout());
    int rowWidth = 0;
    GridBagConstraints c = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0);
    for (JComponent component : myComponents) {
      if (!component.isVisible()) continue;
      int minWidth = component.getMinimumSize().width;
      if (rowWidth + minWidth > parentWidth) {
        add(row);
        add(Box.createVerticalStrut(2));
        row = new JPanel(new GridBagLayout());
        rowWidth = 0;
        c.gridx = 0;
      }
//      c.weightx = minWidth;
      row.add(component, c.clone());
      c.gridx++;
      rowWidth += minWidth;
    }
    add(row);
  }

  public static void setMinimumWidth(Component component, int width) {
    Dimension size = new Dimension(width, component.getMinimumSize().height);
    component.setMinimumSize(size);
    component.setPreferredSize(size);
  }
}
