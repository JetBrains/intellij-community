// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import net.miginfocom.swing.MigLayout;

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

  @Override
  public Component add(Component comp) {
    super.add(comp, "span, wrap");
    return comp;
  }

}
