// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class CommandLinePanel extends JPanel {

  private final List<JComponent> myComponents;
  private final JLabel myHintLabel;
  private int myLastWidth;

  public CommandLinePanel(Collection<? extends SettingsEditorFragment<?,?>> fragments) {
    super();
    myComponents = ContainerUtil.map(fragments, fragment -> fragment.createEditor());
    myHintLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("");
    FragmentHintManager manager = new FragmentHintManager(s -> myHintLabel.setText(s), null);
    manager.registerFragments(fragments);

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setMinimumSize(new Dimension(500, 30));
    buildRows();
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        buildRows();
      }
    });
  }

  public void rebuildRows() {
    myLastWidth = -1;
    buildRows();
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
        add(Box.createVerticalStrut(FragmentedSettingsBuilder.TOP_INSET));
        row = new JPanel(new GridBagLayout());
        rowWidth = 0;
        c.gridx = 0;
      }
      row.add(component, c.clone());
      c.gridx++;
      rowWidth += minWidth;
    }
    add(row);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myHintLabel, BorderLayout.WEST);
    Dimension size = new Dimension(100, 20);
    panel.setMinimumSize(size);
    panel.setPreferredSize(size);
    panel.setBorder(JBUI.Borders.emptyLeft(getLeftInset()));
    add(panel);
  }

  public int getLeftInset() {
    return Arrays.stream(getComponents()).map(component -> FragmentedSettingsBuilder
      .getLeftInset((JComponent)component)).max(Comparator.comparingInt(o -> o))
      .orElse(0);
  }

  public static void setMinimumWidth(Component component, int width) {
    Dimension size = new Dimension(width, component.getMinimumSize().height);
    component.setMinimumSize(size);
    component.setPreferredSize(size);
  }
}
