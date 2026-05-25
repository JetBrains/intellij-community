// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode
import com.intellij.execution.dashboard.actions.ExecutorAction
import com.intellij.execution.dashboard.actions.ExecutorAction.isRunning
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.SmartList

@Suppress("removal", "DEPRECATION")
internal sealed class DashboardExecutorAction : ExecutorAction() {
  final override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      update(e, false)
      return
    }

    val targetNodes = RunDashboardActionSelection.getLeafTargets(e).toList()
    val running = isAnythingRunningInSelection(targetNodes) || isContextualDescriptorNotTerminated(e)
    update(e, running)

    val runnableLeaves = getRunnableLeaves(targetNodes, project)
    val presentation = e.presentation
    presentation.putClientProperty(RUNNABLE_LEAVES_KEY, runnableLeaves.ifEmpty { null })
    presentation.isEnabled = runnableLeaves.isNotEmpty()
    presentation.isVisible = targetNodes.isNotEmpty()
  }

  private fun getRunnableLeaves(targetNodes: List<RunDashboardRunConfigurationNode>, project: Project): List<Int> {
    val runnableLeaves = SmartList<Int>()
    for (i in targetNodes.indices) {
      if (canRun(targetNodes[i], project)) {
        runnableLeaves.add(i)
      }
    }
    return runnableLeaves
  }

  private fun canRun(node: RunDashboardRunConfigurationNode, project: Project): Boolean {
    ProgressManager.checkCanceled()
    val settings = node.configurationSettings ?: return false
    return canRun(settings, null, DumbService.isDumb(project), executor)
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val targetNodes = RunDashboardActionSelection.getLeafTargets(e).toList()
    var runnableLeaves = e.presentation.getClientProperty(RUNNABLE_LEAVES_KEY)
    if (runnableLeaves == null) {
      runnableLeaves = getRunnableLeaves(targetNodes, project)
      if (runnableLeaves.isEmpty()) {
        return
      }
    }

    val executor = executor
    for (i in runnableLeaves) {
      if (targetNodes.size > i) {
        val node = targetNodes[i]
        val settings = node.configurationSettings ?: continue
        run(settings, node.descriptor, e.dataContext, executor)
      }
    }
  }
}

private val RUNNABLE_LEAVES_KEY: Key<List<Int>> = Key.create("RUN_DASHBOARD_RUNNABLE_LEAVES_KEY")

private fun isAnythingRunningInSelection(targetNodes: List<RunDashboardRunConfigurationNode>): Boolean {
  return targetNodes.any { isRunning(it) }
}

private fun isContextualDescriptorNotTerminated(e: AnActionEvent): Boolean {
  return e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR)?.processHandler?.isProcessTerminated == false
}
