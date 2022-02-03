// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class TogglePowerSaveAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return PowerSaveMode.isEnabled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    PowerSaveMode.setEnabled(state);
    if (state) {
      PowerSaveModeNotifier.notifyOnPowerSaveMode(e.getData(CommonDataKeys.PROJECT));
    }
  }
}
