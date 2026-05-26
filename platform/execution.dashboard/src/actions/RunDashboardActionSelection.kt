// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.LegacyRunDashboardServiceSubstitutor
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode
import com.intellij.execution.dashboard.RunDashboardService
import com.intellij.execution.dashboard.actions.RunDashboardGroupNode
import com.intellij.execution.services.ServiceViewActionUtils
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.productMode.IdeProductMode.Companion.isMonolith
import com.intellij.util.containers.JBIterable

internal object RunDashboardActionSelection {
  @JvmStatic
  fun getTarget(e: AnActionEvent): RunDashboardRunConfigurationNode? {
    return ServiceViewActionUtils.getTarget(e, RunDashboardRunConfigurationNode::class.java)
  }

  @JvmStatic
  fun getLeafTargets(e: AnActionEvent): JBIterable<RunDashboardRunConfigurationNode> {
    val project = e.project ?: return JBIterable.empty()
    val uiSelection = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS)
                      ?: return getFallbackSelectionForEmbeddedBackendRunToolwindowActions(e, project)
    return getLeafTargetsFromUiSelection(e, project, uiSelection)
  }
}

private fun getLeafTargetsFromUiSelection(
  e: AnActionEvent,
  project: Project,
  uiSelection: Array<Any>,
): JBIterable<RunDashboardRunConfigurationNode> {
  val result = LinkedHashSet<RunDashboardRunConfigurationNode>()
  if (!collectLeaves(
      project = project,
      e = e,
      items = uiSelection.asList(),
      result = result
    )
  ) return JBIterable.empty()

  val selectedNodes = JBIterable.from(result)
  if (!isMonolith) {
    return selectedNodes
  }

  val substitutor = LegacyRunDashboardServiceSubstitutor.EP_NAME.extensionList.firstOrNull() ?: return selectedNodes
  return JBIterable.from(result.map { substitutor.substituteWithBackendService(it, project) })
}

private fun getFallbackSelectionForEmbeddedBackendRunToolwindowActions(
  e: AnActionEvent,
  project: Project,
): JBIterable<RunDashboardRunConfigurationNode> {
  val currentContentDescriptorId = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR)?.id ?: return JBIterable.empty()

  // Backend case with a non-split Run tool window where frontend data context serialization does not carry Service View selection.
  val maybeService = RunDashboardManager.getInstance(project).findService(currentContentDescriptorId)
  return JBIterable.of(maybeService as? RunDashboardService ?: return JBIterable.empty())
}

private fun collectLeaves(
  project: Project,
  e: AnActionEvent,
  items: List<*>,
  result: MutableSet<RunDashboardRunConfigurationNode>,
): Boolean {
  for (item in items) {
    when (item) {
        is RunDashboardGroupNode -> {
          if (!collectLeaves(project, e, item.getChildren(project, e), result)) {
            return false
          }
        }
      is RunDashboardRunConfigurationNode -> {
        result.add(item)
      }
      is AbstractTreeNode<*> -> {
        val node = item.parent as? RunDashboardRunConfigurationNode ?: return false
        result.add(node)
      }
      else -> {
        return false
      }
    }
  }
  return true
}
