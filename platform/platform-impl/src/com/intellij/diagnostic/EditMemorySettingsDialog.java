// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public class EditMemorySettingsDialog extends DialogWrapper {
  private static final int MIN_VALUE = 256, HEAP_INCREMENT = 512;

  private final VMOptions.MemoryKind myOption;
  private final int myLowerBound;
  private final EditMemorySettingsPanel content;
  private Action mySaveAndExitAction, mySaveAction;

  EditMemorySettingsDialog() {
    this(VMOptions.MemoryKind.HEAP, false);
  }

  EditMemorySettingsDialog(@NotNull VMOptions.MemoryKind option) {
    this(option, true);
  }

  private EditMemorySettingsDialog(VMOptions.MemoryKind option, boolean memoryLow) {
    super(true);
    myOption = option;
    myLowerBound = Math.max(option == VMOptions.MemoryKind.HEAP ? VMOptions.readOption(VMOptions.MemoryKind.MIN_HEAP, false) : 0, MIN_VALUE);
    setTitle(DiagnosticBundle.message("change.memory.title"));
    content = new EditMemorySettingsPanel(option, memoryLow, getSuggestedValue(memoryLow, myOption));
    init();
    initValidation();
  }

  private static int getSuggestedValue(boolean memoryLow, VMOptions.MemoryKind option) {
    var current = VMOptions.readOption(option, true);
    var suggested = 0;
    if (memoryLow && option == VMOptions.MemoryKind.HEAP) {
      var cap = Registry.intValue("max.suggested.heap.size");
      if (current > 0) {
        suggested = current + HEAP_INCREMENT;
        if (suggested > cap) suggested = Math.max(cap, current);
      }
      else {
        suggested = cap;
      }
    }
    else {
      suggested = VMOptions.readOption(option, false);
      if (suggested <= 0) suggested = current;
      if (suggested <= 0) suggested = MIN_VALUE;
    }
    return suggested;
  }

  @Override
  protected JComponent createCenterPanel() {
    return content.panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    var canRestart = ApplicationManager.getApplication().isRestartCapable();
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
  protected @Nullable ValidationInfo doValidate() {
    ValidationInfo info = null;
    try {
      var value = Integer.parseInt(content.newValueField.getText());
      if (value <= myLowerBound) info = new ValidationInfo(DiagnosticBundle.message("change.memory.low", myLowerBound), content.newValueField);
    }
    catch (NumberFormatException e) {
      info = new ValidationInfo(UIBundle.message("please.enter.a.number"), content.newValueField);
    }
    mySaveAndExitAction.setEnabled(info == null);
    mySaveAction.setEnabled(info == null);
    return info;
  }

  private boolean save() {
    try {
      var value = Integer.parseInt(content.newValueField.getText());
      EditMemorySettingsService.getInstance().save(myOption, value);
      return true;
    }
    catch (IOException e) {
      Messages.showErrorDialog(content.newValueField, IoErrorText.message(e), OptionsBundle.message("cannot.save.settings.default.dialog.title"));
      return false;
    }
  }
}
