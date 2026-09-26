// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.actions.ExecutorAction
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction

internal class StopAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    if (e.project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    val enabled = RunDashboardActionSelection.getLeafTargets(e)
      .filter { ExecutorAction.isRunning(it) }
      .isNotEmpty
    presentation.isEnabled = enabled
    presentation.isVisible = enabled || !e.isFromContextMenu
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (e.project == null) {
      return
    }

    for (node in RunDashboardActionSelection.getLeafTargets(e)) {
      ExecutionManagerImpl.stopProcess(node.descriptor)
    }
  }
}
