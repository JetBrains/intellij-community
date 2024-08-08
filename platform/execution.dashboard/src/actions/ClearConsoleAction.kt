// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.actions.RunDashboardActionUtils
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ClearConsoleAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    val targetNodes = RunDashboardActionUtils.getLeafTargets(e)
    val enabled = targetNodes.filter {
      if (it.content == null) return@filter false
      val size = (it.descriptor?.executionConsole as? ConsoleView)?.getContentSize() ?: return@filter false
      size > 0
    }.isNotEmpty
    presentation.isEnabled = enabled
    presentation.isVisible = enabled || !ActionPlaces.isPopupPlace(e.place)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return

    for (node in RunDashboardActionUtils.getLeafTargets(e)) {
      (node.descriptor?.executionConsole as? ConsoleView)?.clear()
    }
  }
}