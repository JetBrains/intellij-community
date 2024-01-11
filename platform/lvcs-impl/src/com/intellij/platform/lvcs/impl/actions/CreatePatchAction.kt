// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.diff.getChanges

class CreatePatchAction : RevisionSelectionAction() {

  override fun actionPerformed(project: Project, gateway: IdeaGateway, activityScope: ActivityScope, selection: RevisionSelection) {
    // see com.intellij.history.integration.ui.models.HistoryDialogModel.canPerformCreatePatch
    if (selection.leftEntry?.hasUnavailableContent() == true || selection.rightEntry?.hasUnavailableContent() == true) {
      thisLogger<CreatePatchAction>().error("Unavailable content")
      return
    }

    CreatePatchFromChangesAction.createPatch(project, null, selection.getChanges(gateway, activityScope).toList())
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}