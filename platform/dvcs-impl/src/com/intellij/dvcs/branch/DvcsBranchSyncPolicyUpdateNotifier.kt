// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch

import com.intellij.dvcs.DvcsNotificationIdsHolder
import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsNotifier

class DvcsBranchSyncPolicyUpdateNotifier<Repo : Repository>(
  private val project: Project,
  private val vcs: AbstractVcs,
  private val dvcsSyncSettings: DvcsSyncSettings,
  private val repositoryManager: AbstractRepositoryManager<Repo>
) {

  fun initBranchSyncPolicyIfNotInitialized() {
    if (repositoryManager.moreThanOneRoot() && dvcsSyncSettings.syncSetting == DvcsSyncSettings.Value.NOT_DECIDED) {
      if (repositoryManager.shouldProposeSyncControl()) {
        notifyAboutSyncedBranches()
        dvcsSyncSettings.syncSetting = DvcsSyncSettings.Value.SYNC
      }
      else {
        dvcsSyncSettings.syncSetting = DvcsSyncSettings.Value.DONT_SYNC
      }
    }
  }

  private fun notifyAboutSyncedBranches() {
    VcsNotifier.getInstance(project).notify(
      VcsNotifier.STANDARD_NOTIFICATION
        .createNotification(DvcsBundle.message("notification.message.branch.operations.are.executed.on.all.roots"),
                            NotificationType.INFORMATION)
        .setDisplayId(DvcsNotificationIdsHolder.BRANCH_OPERATIONS_ON_ALL_ROOTS)
        .addAction(
          NotificationAction.create(DvcsBundle.message("action.NotificationAction.DvcsBranchPopup.text.disable")
          ) { _: AnActionEvent?, notification: Notification ->
            ShowSettingsUtil.getInstance().showSettingsDialog(project,
                                                              vcs.displayName)
            if (dvcsSyncSettings.syncSetting == DvcsSyncSettings.Value.DONT_SYNC) {
              notification.expire()
            }
          }))
  }
}
