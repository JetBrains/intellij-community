// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.psi.statistics.StatisticsManager
import kotlinx.coroutines.launch

/** Internal action that drops all IDE statistics collected by [StatisticsManager], both in memory and on disk. */
internal class ClearIDEStatisticsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val answer = Messages.showYesNoDialog(
      e.project,
      "Drop all collected IDE statistics?\nThis cannot be undone.",
      "Clear IDE Statistics",
      Messages.getWarningIcon(),
    )
    if (answer != Messages.YES) return

    e.coroutineScope.launch {
      StatisticsManager.getInstance().clearStatistics()

      val notification = Notification(
        "Clear IDE Statistics",
        "IDE statistics dropped",
        "All collected IDE statistics have been dropped.",
        NotificationType.INFORMATION,
      )
      NotificationsManager.getNotificationsManager().showNotification(notification, e.project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
