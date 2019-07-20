// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
import java.nio.file.Path

class WindowsDefenderCheckerActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) return

    val windowsDefenderChecker = WindowsDefenderChecker.getInstance()
    if (windowsDefenderChecker.isVirusCheckIgnored(project)) return

    val checkResult = windowsDefenderChecker.checkWindowsDefender(project)
    if (checkResult.status == WindowsDefenderChecker.RealtimeScanningStatus.SCANNING_ENABLED &&
        checkResult.pathStatus.any { !it.value }) {

      val nonExcludedPaths = checkResult.pathStatus.filter { !it.value }.keys
      val notification = WindowsDefenderNotification(
        DiagnosticBundle.message("virus.scanning.warn.message", ApplicationNamesInfo.getInstance().fullProductName,
                                 nonExcludedPaths.joinToString("<br/>")),
        nonExcludedPaths
      )
      notification.isImportant = true
      notification.collapseActionsDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
      windowsDefenderChecker.configureActions(project, notification)

      app.invokeLater {
        notification.notify(project)
      }
    }
  }
}

class WindowsDefenderNotification(text: String, val paths: Collection<Path>) :
  Notification("System Health",  "", text, NotificationType.WARNING), NotificationFullContent

class WindowsDefenderFixAction(val paths: Collection<Path>) : NotificationAction("Fix...") {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    val rc = Messages.showDialog(
      e.project,
      DiagnosticBundle.message("virus.scanning.fix.explanation", ApplicationNamesInfo.getInstance().fullProductName,
                               WindowsDefenderChecker.getInstance().configurationInstructionsUrl),
      DiagnosticBundle.message("virus.scanning.fix.title"),
      arrayOf(
        DiagnosticBundle.message("virus.scanning.fix.automatically"),
        DiagnosticBundle.message("virus.scanning.fix.manually"),
        CommonBundle.getCancelButtonText()
      ),
      0,
      null)
    when (rc) {
      0 -> {
        notification.expire()
        ApplicationManager.getApplication().executeOnPooledThread {
          if (WindowsDefenderChecker.getInstance().runExcludePathsCommand(e.project, paths)) {
            UIUtil.invokeLaterIfNeeded {
              Notifications.Bus.notify(Notification("System Health", "", DiagnosticBundle.message("virus.scanning.fix.success.notification"),
                                                    NotificationType.INFORMATION))
            }
          }
        }
      }
      1 -> BrowserUtil.browse(WindowsDefenderChecker.getInstance().configurationInstructionsUrl)
    }
  }
}
