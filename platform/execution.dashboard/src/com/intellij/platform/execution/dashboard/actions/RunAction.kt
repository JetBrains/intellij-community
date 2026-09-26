// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.ExperimentalUI

internal class RunAction : DashboardExecutorAction() {
  override fun getExecutor(): Executor = DefaultRunExecutor.getRunExecutorInstance()

  override fun update(e: AnActionEvent, running: Boolean) {
    val presentation = e.presentation
    if (running) {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.rerun.action.name"))
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.rerun.action.description"))
      presentation.icon = if (ExperimentalUI.isNewUI()) DefaultRunExecutor.getRunExecutorInstance().rerunIcon else AllIcons.Actions.Restart
    }
    else {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.run.action.name"))
      presentation.setDescription(ExecutionBundle.messagePointer("run.dashboard.run.action.description"))
      presentation.icon = AllIcons.Actions.Execute
    }
  }
}
