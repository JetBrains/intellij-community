// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.lvcs.impl.USE_OLD_CONTENT
import com.intellij.platform.lvcs.impl.diff.PresentableFileDifference
import com.intellij.platform.lvcs.impl.hasMultipleFiles
import com.intellij.platform.lvcs.impl.operations.createDifferenceReverter
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.toChangeSetSelection
import com.intellij.platform.lvcs.impl.ui.ActivityViewDataKeys

internal class RevertDifferencesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    if (e.project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val activityScope = e.getData(ActivityViewDataKeys.SCOPE)
    val activitySelection = e.getData(ActivityViewDataKeys.SELECTION)
    val activityDifferences = e.getData(ActivityViewDataKeys.SELECTED_DIFFERENCES)
    if (activityScope == null || !activityScope.hasMultipleFiles || activityDifferences == null || activitySelection == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = activityDifferences.any()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activityScope = e.getData(ActivityViewDataKeys.SCOPE) ?: return
    val selection = e.getData(ActivityViewDataKeys.SELECTION)?.toChangeSetSelection() ?: return
    val differences = e.getData(ActivityViewDataKeys.SELECTED_DIFFERENCES)
      ?.filterIsInstance<PresentableFileDifference>()?.map { it.difference } ?: return

    val localHistoryImpl = LocalHistoryImpl.getInstanceImpl()
    val facade = localHistoryImpl.facade ?: return
    val gateway = localHistoryImpl.gateway

    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.RevertChanges, activityScope)

    val reverter = facade.createDifferenceReverter(project, gateway, selection, differences, USE_OLD_CONTENT)
    if (reverter == null || reverter.checkCanRevert().isNotEmpty()) return
    reverter.revert()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}