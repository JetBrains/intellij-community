// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowId

internal class DebugAction : DashboardExecutorAction() {
  override fun getExecutor(): Executor = checkNotNull(ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG))

  override fun update(e: AnActionEvent, running: Boolean) {
    val presentation = e.presentation
    if (running) {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.restart.debugger.action.name"))
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.restart.debugger.action.description"))
      presentation.icon = AllIcons.Actions.RestartDebugger
    }
    else {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.debug.action.name"))
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.debug.action.description"))
      presentation.icon = AllIcons.Actions.StartDebugger
    }
  }
}
