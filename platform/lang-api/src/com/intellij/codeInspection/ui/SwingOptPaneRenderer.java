// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class SwingOptPaneRenderer implements InspectionOptionPaneRenderer {
  @Override
  public @NotNull JComponent render(@NotNull InspectionProfileEntry entry, @NotNull OptPane pane, @NotNull Map<@NotNull String, @NotNull JComponent> customControls) {
    Map<OptCustom, JComponent> controls = mapControls(pane, customControls);
    InspectionOptionsPanel panel = new InspectionOptionsPanel(entry);
    for (OptComponent component : pane.components()) {
      if (component instanceof OptCheckbox checkbox) {
        panel.addCheckbox(checkbox.label().label(), checkbox.bindId());
      }
      else if (component instanceof OptCustom custom) {
        JComponent jComponent = controls.get(custom);
        if (jComponent == null) {
          throw new IllegalArgumentException("Custom control '" + custom.bindId() + "' is not installed");
        }
        panel.add(jComponent);
      }
      else {
        throw new UnsupportedOperationException("Control " + component.getClass() + " is not supported yet");
      }
    }
    return panel;
  }

  private static @NotNull Map<@NotNull OptCustom, @NotNull JComponent> mapControls(
    @NotNull OptPane pane,
    @NotNull Map<@NotNull String, @NotNull JComponent> customControls) {
    if (customControls.isEmpty()) return Map.of();
    Map<@NotNull OptCustom, @NotNull JComponent> map = new HashMap<>();
    customControls.forEach((bindId, control) -> {
      OptControl optControl = pane.findControl(bindId);
      if (optControl == null) {
        throw new IllegalArgumentException("Control '" + bindId + "' not found");
      }
      if (!(optControl instanceof OptCustom custom)) {
        throw new IllegalArgumentException("Control '" + bindId + "' is not a custom control");
      }
      JComponent old = map.putIfAbsent(custom, control);
      if (old != null) {
        throw new IllegalStateException("Control '" + bindId + "' was already installed");
      }
    });
    return map;
  }
}
