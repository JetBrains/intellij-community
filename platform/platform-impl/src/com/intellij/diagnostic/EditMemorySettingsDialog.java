// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.system.CpuArch;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;

import static java.awt.GridBagConstraints.*;
import static javax.swing.SwingConstants.RIGHT;

@ApiStatus.NonExtendable
public class EditMemorySettingsDialog extends DialogWrapper {
  private static final int MIN_VALUE = 256, STEP = 512;

  private final VMOptions.MemoryKind myOption;
  private final boolean myLowHeap;
  private JTextField myNewValueField;

  public EditMemorySettingsDialog(@NotNull VMOptions.MemoryKind option, boolean lowHeap) {
    super(false);
    myOption = option;
    myLowHeap = lowHeap && option == VMOptions.MemoryKind.HEAP;
    setTitle(DiagnosticBundle.message("change.memory.title"));
    init();
    initValidation();
  }

  @Override
  protected JComponent createCenterPanel() {
    int current = VMOptions.readOption(myOption, true), suggested;
    if (myLowHeap) {
      int cap = CpuArch.isIntel32() ? 800 : Registry.intValue("max.suggested.heap.size");
      if (current > 0) {
        suggested = current + STEP;
        if (suggested > cap) suggested = Math.max(cap, current);
      }
      else {
        suggested = cap;
      }
    }
    else {
      suggested = VMOptions.readOption(myOption, false);
      if (suggested <= 0) suggested = current;
      if (suggested <= 0) suggested = MIN_VALUE;
    }

    Path file = VMOptions.getWriteFile();
    assert file != null;

    JPanel panel = new JPanel(new GridBagLayout());

    String text;
    if (myLowHeap) {
      long free = Runtime.getRuntime().freeMemory() >> 20, max = Runtime.getRuntime().maxMemory() >> 20;
      text = DiagnosticBundle.message("change.memory.usage", String.valueOf(free), String.valueOf(max));
    }
    else {
      text = DiagnosticBundle.message("change.memory.message");
    }
    panel.add(new JBLabel(text), new GridBagConstraints(0, 0, 4, 1, 1.0, 1.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));

    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.act")),
              new GridBagConstraints(0, 1, 4, 1, 1.0, 1.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));

    JBLabel prompt = new JBLabel(myOption.label() + ':', RIGHT);
    prompt.setToolTipText('-' + myOption.optionName);
    panel.add(prompt, new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, EAST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    myNewValueField = new JTextField(5);
    myNewValueField.setText(String.valueOf(suggested));
    panel.add(myNewValueField, new GridBagConstraints(1, 2, 1, 1, 0.0, 1.0, CENTER, NONE, JBUI.insets(10, 10, 10, 2), 0, 0));

    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.units")),
              new GridBagConstraints(2, 2, 1, 1, 0.0, 1.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));

    String formatted = current == -1 ? DiagnosticBundle.message("change.memory.unknown") : String.valueOf(current);
    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.current", formatted), RIGHT).withFont(JBFont.label().asItalic()),
              new GridBagConstraints(3, 2, 1, 1, 0.0, 1.0, WEST, NONE, JBUI.insetsLeft(10), 0, 0));

    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.file", file), AllIcons.General.Information, RIGHT),
              new GridBagConstraints(0, 3, 4, 1, 1.0, 1.0, EAST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    return panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
    return new Action[]{
      new DialogWrapperAction(DiagnosticBundle.message(canRestart ? "change.memory.apply" : "change.memory.exit")) {
        @Override
        protected void doAction(ActionEvent e) {
          if (save()) {
            ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
          }
        }
      },
      new DialogWrapperAction(IdeBundle.message("button.save")) {
        @Override
        protected void doAction(ActionEvent e) {
          if (save()) {
            close(OK_EXIT_CODE);
          }
        }
      },
      getCancelAction()
    };
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNewValueField;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    try {
      int value = Integer.parseInt(myNewValueField.getText());
      if (value < MIN_VALUE) return new ValidationInfo(DiagnosticBundle.message("change.memory.low", MIN_VALUE), myNewValueField);
      if (value > 800 && CpuArch.isIntel32()) return new ValidationInfo(DiagnosticBundle.message("change.memory.large"), myNewValueField);
      return null;
    }
    catch (NumberFormatException e) {
      return new ValidationInfo(DiagnosticBundle.message("change.memory.integer"), myNewValueField);
    }
  }

  private boolean save() {
    try {
      int value = Integer.parseInt(myNewValueField.getText());
      VMOptions.writeOption(myOption, value);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }
}
