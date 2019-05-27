// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class WindowsDefenderCheckerActivity : StartupActivity {
  override fun runActivity(project: Project) {
    if (!ApplicationManager.getApplication().isInternal) return
    ApplicationManager.getApplication().executeOnPooledThread {
      val checkResult = WindowsDefenderChecker.getInstance().checkWindowsDefender(project)
      if (checkResult.status == WindowsDefenderChecker.RealtimeScanningStatus.SCANNING_ENABLED &&
          checkResult.pathStatus.any { !it.value }) {

        val notification = WindowsDefenderNotification(
          DiagnosticBundle.message("virus.scanning.warn.message", ApplicationNamesInfo.getInstance().fullProductName,
                                   WindowsDefenderChecker.getNotificationTextForNonExcludedPaths(checkResult.pathStatus)))
        notification.isImportant = true

        ApplicationManager.getApplication().invokeLater {
          Notifications.Bus.notify(notification)
        }
      }
    }
  }
}

private class WindowsDefenderNotification(text: String) :
  Notification("System Health",  "", text, NotificationType.WARNING), NotificationFullContent
