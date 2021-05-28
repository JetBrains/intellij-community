// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class CommandLinePanel extends JPanel {
  private final List<JComponent> myComponents;
  private final JLabel myHintLabel;

  public CommandLinePanel(Collection<? extends SettingsEditorFragment<?,?>> fragments, @NotNull Disposable disposable) {
    super();
    myComponents = ContainerUtil.map(fragments, fragment -> fragment.createEditor());
    myHintLabel = ComponentPanelBuilder.createNonWrappingCommentComponent("");
    String keystrokeText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ALT, 0));
    FragmentHintManager manager = new FragmentHintManager(s -> myHintLabel.setText(s),
                                                          IdeBundle.message("dialog.message.press.for.field.hints", keystrokeText),
                                                          disposable);
    manager.registerFragments(fragments);

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    buildRows();
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(Math.min(700, size.width), size.height);
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

  public static Dimension setMinimumWidth(Component component, int width) {
    Dimension size = new Dimension(width, Math.max(JBUI.scale(30), component.getMinimumSize().height));
    component.setMinimumSize(size);
    if (component instanceof RawCommandLineEditor) {
      ((RawCommandLineEditor)component).getTextField().setColumns(0);
    }
    return size;
  }
}
