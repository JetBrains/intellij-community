// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

/**
 * @author konstantin.aleev
 */
// fixme - tbd, ask Lera?
public final class DebugAction extends ExecutorAction {
  @Override
  protected Executor getExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
  }

  @Override
  protected void update(@NotNull AnActionEvent e, boolean running) {
    Presentation presentation = e.getPresentation();
    if (running) {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.restart.debugger.action.name"));
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.restart.debugger.action.description"));
      presentation.setIcon(AllIcons.Actions.RestartDebugger);
    }
    else {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.debug.action.name"));
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.debug.action.description"));
      presentation.setIcon(AllIcons.Actions.StartDebugger);
    }
  }
}
