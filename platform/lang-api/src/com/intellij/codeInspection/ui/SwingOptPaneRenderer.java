// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwingOptPaneRenderer implements InspectionOptionPaneRenderer {
  @Override
  public @NotNull JComponent render(@NotNull InspectionProfileEntry tool, @NotNull OptPane pane, @NotNull CustomComponentProvider customControls) {
    InspectionOptionsPanel panel = new InspectionOptionsPanel(tool);
    for (OptComponent component : pane.components()) {
      if (component instanceof OptCheckbox checkbox) {
        panel.addCheckbox(checkbox.label().label(), checkbox.bindId());
        // TODO: support nested controls
      }
      else if (component instanceof OptNumber number) {
        LocMessage.PrefixSuffix prefixSuffix = number.splitLabel().splitLabel();
        JFormattedTextField field = SingleIntegerFieldOptionsPanel.createIntegerFieldTrackingValue(tool, number.bindId(), 4);
        panel.addLabeledRow(prefixSuffix.prefix(), field);
        // TODO: support suffix
        // TODO: range validation
      }
      else if (component instanceof OptCustom custom) {
        JComponent jComponent = customControls.getCustomOptionComponent(custom, panel);
        panel.add(jComponent);
      }
      else {
        throw new UnsupportedOperationException("Control " + component.getClass() + " is not supported yet");
      }
    }
    return panel;
  }
}
