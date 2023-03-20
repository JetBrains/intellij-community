// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PauseOutputAction extends ToggleAction implements DumbAware {

  PauseOutputAction() {
    super(ExecutionBundle.messagePointer("run.configuration.pause.output.action.name"), AllIcons.Actions.Pause);
  }

  private static @Nullable ConsoleView getConsoleView(AnActionEvent event) {
    return event.getData(LangDataKeys.CONSOLE_VIEW);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    ConsoleView consoleView = getConsoleView(event);
    return consoleView != null && consoleView.isOutputPaused();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean flag) {
    ConsoleView consoleView = getConsoleView(event);
    if (consoleView != null) {
      consoleView.setOutputPaused(flag);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    ConsoleView consoleView = getConsoleView(event);

    boolean isEnabled;
    if (consoleView == null || !consoleView.canPause()) {
      isEnabled = false;
    }
    else {
      RunContentDescriptor descriptor = StopAction.getRecentlyStartedContentDescriptor(event.getDataContext());
      ProcessHandler handler = descriptor != null ? descriptor.getProcessHandler() : null;
      isEnabled = handler != null && !handler.isProcessTerminated() ||
                  consoleView.hasDeferredOutput();
    }

    Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(isEnabled);
  }
}