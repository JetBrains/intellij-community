// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ui.InspectionOptionPaneRenderer;
import com.intellij.codeInspection.ui.UiDslOptPaneRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.pane;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptCustomTest {
  private static class MyInspection extends LocalInspectionTool implements InspectionOptionPaneRenderer.CustomComponentProvider {
    public int x = 2;
    public int y = 3;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        new OptCustom("x"),
        new OptCustom("y")
      );
    }

    @Override
    public @NotNull JComponent getCustomOptionComponent(@NotNull OptCustom control, @NotNull JComponent parent) {
      return switch (control.componentId()) {
        case "x" -> new JLabel(getOption("x").toString());
        case "y" -> new JButton(getOption("y").toString());
        default -> throw new IllegalStateException("Unexpected value: " + control.componentId());
      };
    }
  }
  
  @Test
  public void customControls() {
    MyInspection inspection = new MyInspection();
    JComponent component = new UiDslOptPaneRenderer().render(inspection);
    JButton button = UIUtil.findComponentOfType(component, JButton.class);
    assertEquals("3", button.getText());
    JLabel label = UIUtil.findComponentOfType(component, JLabel.class);
    assertEquals("2", label.getText());
  }
}
