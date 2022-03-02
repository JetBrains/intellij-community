// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.system.CpuArch;
import com.intellij.util.ui.IoErrorText;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;

import static java.awt.GridBagConstraints.*;
import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingConstants.RIGHT;

@ApiStatus.NonExtendable
public class EditMemorySettingsDialog extends DialogWrapper {
  private static final int MIN_VALUE = 256, HEAP_INC = 512;

  private final VMOptions.MemoryKind myOption;
  private final boolean myMemoryLow;
  private final int myLowerBound;
  private JTextField myNewValueField;
  private Action mySaveAndExitAction, mySaveAction;

  public EditMemorySettingsDialog() {
    this(VMOptions.MemoryKind.HEAP, false);
  }

  EditMemorySettingsDialog(@NotNull VMOptions.MemoryKind option) {
    this(option, true);
  }

  private EditMemorySettingsDialog(VMOptions.MemoryKind option, boolean memoryLow) {
    super(true);
    myOption = option;
    myMemoryLow = memoryLow;
    myLowerBound = Math.max(option == VMOptions.MemoryKind.HEAP ? VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, false) : 0, MIN_VALUE);
    setTitle(DiagnosticBundle.message("change.memory.title"));
    init();
    initValidation();
  }

  @Override
  protected JComponent createCenterPanel() {
    int current = VMOptions.readOption(myOption, true), suggested;
    if (myMemoryLow && myOption == VMOptions.MemoryKind.HEAP) {
      int cap = CpuArch.isIntel32() ? 800 : Registry.intValue("max.suggested.heap.size");
      if (current > 0) {
        suggested = current + HEAP_INC;
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

    Path file = VMOptions.getUserOptionsFile();
    if (file == null) throw new IllegalStateException();

    JPanel panel = new JPanel(new GridBagLayout());

    if (myMemoryLow) {
      String text;
      if (myOption == VMOptions.MemoryKind.HEAP) {
        long free = Runtime.getRuntime().freeMemory() >> 20, max = Runtime.getRuntime().maxMemory() >> 20;
        text = DiagnosticBundle.message("change.memory.usage", String.valueOf(free), String.valueOf(max));
      }
      else {
        text = DiagnosticBundle.message("change.memory.message");
      }
      panel.add(new JBLabel(text), new GridBagConstraints(0, 0, 5, 1, 1.0, 1.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    }

    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.act")),
              new GridBagConstraints(0, 1, 5, 1, 1.0, 1.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));

    JBLabel prompt = new JBLabel(myOption.label() + ':', RIGHT);
    prompt.setToolTipText('-' + myOption.optionName);
    panel.add(prompt, new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0, EAST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));

    myNewValueField = new JTextField(5);
    myNewValueField.setText(String.valueOf(suggested));
    panel.add(myNewValueField, new GridBagConstraints(2, 2, 1, 1, 0.0, 1.0, CENTER, NONE, JBUI.insets(10, 10, 10, 2), 0, 0));

    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.units")),
              new GridBagConstraints(3, 2, 1, 1, 0.0, 1.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));

    String formatted = current == -1 ? DiagnosticBundle.message("change.memory.unknown") : String.valueOf(current);
    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.current", formatted), RIGHT).withFont(JBFont.label().asItalic()),
              new GridBagConstraints(4, 2, 1, 1, 0.0, 1.0, WEST, NONE, JBUI.insetsLeft(10), 0, 0));

    panel.add(new JBLabel(AllIcons.General.Information),
              new GridBagConstraints(0, 3, 1, 1, 0.0, 1.0, WEST, NONE, JBUI.insetsRight(2), 0, 0));
    panel.add(new JBLabel(DiagnosticBundle.message("change.memory.file"), LEFT).withFont(JBFont.label().asBold()),
              new GridBagConstraints(1, 3, 4, 1, 1.0, 1.0, WEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    panel.add(new JBLabel(file.toString(), LEFT),
              new GridBagConstraints(1, 4, 4, 1, 1.0, 1.0, WEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));

    return panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
    mySaveAndExitAction = new DialogWrapperAction(DiagnosticBundle.message(canRestart ? "change.memory.apply" : "change.memory.exit")) {
      @Override
      protected void doAction(ActionEvent e) {
        if (save()) {
          ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
        }
      }
    };
    mySaveAction = new DialogWrapperAction(IdeBundle.message("button.save")) {
      @Override
      protected void doAction(ActionEvent e) {
        if (save()) {
          close(OK_EXIT_CODE);
        }
      }
    };
    return new Action[]{mySaveAndExitAction, mySaveAction, getCancelAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNewValueField;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    ValidationInfo info = null;
    try {
      int value = Integer.parseInt(myNewValueField.getText());
      if (value <= myLowerBound) info = new ValidationInfo(DiagnosticBundle.message("change.memory.low", myLowerBound), myNewValueField);
      if (value > 800 && CpuArch.isIntel32()) info = new ValidationInfo(DiagnosticBundle.message("change.memory.large"), myNewValueField);
    }
    catch (NumberFormatException e) {
      info = new ValidationInfo(DiagnosticBundle.message("change.memory.integer"), myNewValueField);
    }
    mySaveAndExitAction.setEnabled(info == null);
    mySaveAction.setEnabled(info == null);
    return info;
  }

  private boolean save() {
    try {
      int value = Integer.parseInt(myNewValueField.getText());
      VMOptions.setOption(myOption, value);
      return true;
    }
    catch (IOException e) {
      Messages.showErrorDialog(myNewValueField, IoErrorText.message(e), OptionsBundle.message("cannot.save.settings.default.dialog.title"));
      return false;
    }
  }
}
