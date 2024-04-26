// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ClearConsoleAction extends DumbAwareAction {

  public ClearConsoleAction() {
    super(ExecutionBundle.messagePointer("clear.all.from.console.action.name"),
          ExecutionBundle.messagePointer("clear.all.from.console.action.description"), AllIcons.Actions.GC);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ConsoleView data = e.getData(LangDataKeys.CONSOLE_VIEW);
    boolean enabled = data != null && data.getContentSize() > 0;
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final ConsoleView consoleView = e.getData(LangDataKeys.CONSOLE_VIEW);
    if (consoleView != null) {
      consoleView.clear();
    }
  }
}
