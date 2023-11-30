// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeEditor.printing;

import com.intellij.ide.actions.PrintActionHandler;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class PrintAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PrintActionHandler handler = PrintActionHandler.getHandler(e.getDataContext());
    if (handler == null) return;
    handler.print(e.getDataContext());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    PrintActionHandler handler = PrintActionHandler.getHandler(e.getDataContext());
    e.getPresentation().setEnabled(handler != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}