// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InspectionOptionsPanel extends JPanel {

  public InspectionOptionsPanel() {
    super(new MigLayout("fillx, ins 0"));
  }

  public void addRow(Component label, Component component) {
    add(label, "");
    add(component, "pushx, wrap");
  }

  public void addLabeledRow(@NlsContexts.Label String labelText, Component component) {
    final JLabel label = new JLabel(labelText);
    label.setLabelFor(component);
    addRow(label, component);
  }

  /**
   * Adds a row with a single component, using as much vertical and horizontal space as possible.
   */
  public void addGrowing(Component component) {
    add(component, "push, grow, wrap");
  }

  @Override
  public Component add(Component comp) {
    super.add(comp, "span, wrap");
    return comp;
  }

  static public @NotNull Dimension getMinimumListSize() {
    return JBUI.size(150, 100);
  }

}
