// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction

internal class ClearConsoleAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    val targetNodes = RunDashboardActionSelection.getLeafTargets(e)
    val enabled = targetNodes.filter {
      val size = (it.descriptor?.executionConsole as? ConsoleView)?.getContentSize() ?: return@filter false
      size > 0
    }.isNotEmpty()
    presentation.isEnabled = enabled
    presentation.isVisible = enabled || !e.isFromContextMenu
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (e.project == null) {
      return
    }
    for (node in RunDashboardActionSelection.getLeafTargets(e)) {
      (node.descriptor?.executionConsole as? ConsoleView)?.clear()
    }
  }
}