// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CommandLinePanel extends JPanel {
  private final List<JComponent> myComponents;
  private final JLabel myHintLabel;

  public CommandLinePanel(Collection<? extends SettingsEditorFragment<?,?>> fragments) {
    super();
    myComponents = ContainerUtil.map(fragments, fragment -> fragment.createEditor());
    myHintLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("");
    FragmentHintManager manager = new FragmentHintManager(s -> myHintLabel.setText(s), null);
    manager.registerFragments(fragments);

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setMinimumSize(new JBDimension(500, 30));
    buildRows();
  }

  private void buildRows() {
    WrapLayout layout = new WrapLayout(FlowLayout.LEFT, 0, FragmentedSettingsBuilder.TOP_INSET);
    layout.setFillWidth(true);
    JPanel mainPanel = new JPanel(layout);
    for (JComponent component : myComponents) {
      mainPanel.add(component);
    }
    add(mainPanel);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myHintLabel, BorderLayout.WEST);
    JBDimension size = new JBDimension(100, 15);
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
    Dimension size = new Dimension(width, Math.max(JBUI.scale(30), component.getMinimumSize().height));
    component.setMinimumSize(size);
    component.setPreferredSize(size);
  }
}
