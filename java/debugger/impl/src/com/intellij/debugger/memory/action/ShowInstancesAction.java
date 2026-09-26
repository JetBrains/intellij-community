// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import org.jetbrains.annotations.NotNull;

abstract class ShowInstancesAction extends ClassesActionBase {
  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final TypeInfo ref = getSelectedClass(e);
    final boolean enabled = isEnabled(e) && ref != null && ref.canGetInstanceInfo();
    presentation.setEnabled(enabled);
    if (enabled) {
      presentation.setText(getPresentation(e));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private @NlsSafe String getPresentation(@NotNull AnActionEvent e) {
    return String.format("%s (%d)", getLabel(), getInstancesCount(e));
  }

  protected abstract String getLabel();

  protected abstract int getInstancesCount(AnActionEvent e);
}
