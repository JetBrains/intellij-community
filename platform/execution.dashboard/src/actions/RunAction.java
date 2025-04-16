// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

/**
 * @author konstantin.aleev
 */
// fixme same as debug action - ask Lera?
public final class RunAction extends ExecutorAction {
  @Override
  protected Executor getExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  @Override
  protected void update(@NotNull AnActionEvent e, boolean running) {
    Presentation presentation = e.getPresentation();
    if (running) {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.rerun.action.name"));
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.rerun.action.description"));
      presentation.setIcon(
        ExperimentalUI.isNewUI() ? DefaultRunExecutor.getRunExecutorInstance().getRerunIcon() : AllIcons.Actions.Restart);
    }
    else {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.run.action.name"));
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.run.action.description"));
      presentation.setIcon(AllIcons.Actions.Execute);
    }
  }
}
