// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.USE_OLD_CONTENT
import com.intellij.platform.lvcs.impl.diff.getChanges
import com.intellij.platform.lvcs.impl.diff.getDiff

class CreatePatchAction : ChangeSetSelectionAction() {

  override fun actionPerformed(project: Project,
                               facade: LocalHistoryFacade,
                               gateway: IdeaGateway,
                               activityScope: ActivityScope,
                               selection: ChangeSetSelection) {
    val diff = facade.getDiff(gateway, activityScope, selection, USE_OLD_CONTENT)
    if (diff.any { it.left?.hasUnavailableContent() == true || it.right?.hasUnavailableContent() == true }) {
      thisLogger().error("Unavailable content")
      return
    }
    val changes = getChanges(gateway, activityScope, diff)
    CreatePatchFromChangesAction.createPatch(project, null, changes)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}