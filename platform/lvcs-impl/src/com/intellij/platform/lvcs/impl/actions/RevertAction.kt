// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.platform.lvcs.impl.USE_OLD_CONTENT
import com.intellij.platform.lvcs.impl.operations.createReverter
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter

internal class RevertAction : ChangeSetSelectionAction() {

  override fun isEnabled(changeSetSelection: ChangeSetSelection): Boolean {
    return changeSetSelection.rightItem == null || changeSetSelection.leftItem == changeSetSelection.rightItem
  }

  override fun actionPerformed(project: Project,
                               facade: LocalHistoryFacade,
                               gateway: IdeaGateway,
                               activityScope: ActivityScope,
                               selection: ChangeSetSelection,
                               e: AnActionEvent) {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.RevertRevisions, activityScope)

    val diffMode = DirectoryDiffMode.WithLocal // revert everything after the selected changeset
    val reverter = facade.createReverter(project, gateway, activityScope, selection, diffMode, USE_OLD_CONTENT)
    if (reverter == null || reverter.checkCanRevert().isNotEmpty()) return
    reverter.revert()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}