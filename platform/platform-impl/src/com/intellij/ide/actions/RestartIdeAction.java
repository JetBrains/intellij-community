// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.RestartDialogImpl;
import org.jetbrains.annotations.NotNull;

public class RestartIdeAction extends AnAction implements DumbAware {
  public RestartIdeAction() {
    getTemplatePresentation().setApplicationScope(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RestartDialogImpl.restartWithConfirmation();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
