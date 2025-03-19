// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.CommonBundle
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.platform.lvcs.impl.USE_OLD_CONTENT
import com.intellij.platform.lvcs.impl.diff.getDiff
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.ui.ActivityViewDataKeys

internal class CreatePatchAction : ChangeSetSelectionAction() {

  override fun actionPerformed(project: Project,
                               facade: LocalHistoryFacade,
                               gateway: IdeaGateway,
                               activityScope: ActivityScope,
                               selection: ChangeSetSelection,
                               e: AnActionEvent) {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.CreatePatch, activityScope)
    val diffMode = e.getData(ActivityViewDataKeys.DIRECTORY_DIFF_MODE) ?: DirectoryDiffMode.WithLocal

    val changes = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
      val diff = facade.getDiff(gateway, activityScope, selection, diffMode, USE_OLD_CONTENT)
      if (diff.any { it.left?.hasUnavailableContent() == true || it.right?.hasUnavailableContent() == true }) {
        return@ThrowableComputable null
      }
      return@ThrowableComputable diff.map<Difference, Change> { difference ->
        Change(difference.getLeftContentRevision(gateway), difference.getRightContentRevision(gateway))
      }
    }, LocalHistoryBundle.message("activity.action.patch.collecting.diff"), true, project)
    if (changes == null) {
      Messages.showErrorDialog(project, LocalHistoryBundle.message("message.cannot.create.patch.because.of.unavailable.content"),
                               CommonBundle.getErrorTitle())
      return
    }

    CreatePatchFromChangesAction.createPatch(project, null, changes)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}