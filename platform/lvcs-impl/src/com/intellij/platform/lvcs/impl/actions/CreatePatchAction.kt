// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.platform.lvcs.impl.getChanges
import com.intellij.platform.lvcs.impl.toRevisionSelection
import com.intellij.platform.lvcs.ui.ActivityViewDataKeys

class CreatePatchAction : RevisionSelectionAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activityScope = e.getRequiredData(ActivityViewDataKeys.SCOPE)
    val activitySelection = e.getRequiredData(ActivityViewDataKeys.SELECTION)

    val gateway = LocalHistoryImpl.getInstanceImpl().gateway ?: return
    val selection = activitySelection.toRevisionSelection(activityScope) ?: return

    // see com.intellij.history.integration.ui.models.HistoryDialogModel.canPerformCreatePatch
    if (selection.leftEntry?.hasUnavailableContent() == true || selection.rightEntry?.hasUnavailableContent() == true) {
      thisLogger<CreatePatchAction>().error("Unavailable content")
      return
    }

    CreatePatchFromChangesAction.createPatch(project, null, selection.getChanges(gateway, activityScope).toList())
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}