// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowLogAction
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimple
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import java.nio.file.Path

private val LOG = logger<WindowsDefenderCheckerActivity>()

internal class WindowsDefenderCheckerActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val checker = WindowsDefenderChecker.getInstance()
    if (checker.isStatusCheckIgnored(project)) {
      LOG.info("status check is disabled")
      return
    }

    val protection = checker.isRealTimeProtectionEnabled
    if (protection != true) {
      LOG.info("real-time protection: ${protection}")
      return
    }

    val paths = checker.getImportantPaths(project)
    val pathList = paths.joinToString(separator = "<br>&nbsp;&nbsp;", prefix = "<br>&nbsp;&nbsp;") { it.toString() }
    val notification = if (checker.canRunScript()) {
      val auto = DiagnosticBundle.message("defender.config.auto")
      val manual = DiagnosticBundle.message("defender.config.manual")
      notification(DiagnosticBundle.message("defender.config.prompt", pathList, auto, manual), NotificationType.INFORMATION)
        .addAction(createSimpleExpiring(auto) { updateDefenderConfig(checker, project, paths) })
        .addAction(createSimple(manual) { BrowserUtil.browse(checker.configurationInstructionsUrl) })
    }
    else {
      notification(DiagnosticBundle.message("defender.config.prompt.no.script", pathList), NotificationType.INFORMATION)
        .addAction(createSimple(DiagnosticBundle.message("defender.config.instructions")) { BrowserUtil.browse(checker.configurationInstructionsUrl) })
    }
    notification
      .also {
        it.isImportant = true
        it.collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
      }
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress1")) { suppressCheck(checker, project) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress2")) { suppressCheck(checker, null) })
      .notify(project)
  }

  private fun updateDefenderConfig(checker: WindowsDefenderChecker, project: Project, paths: List<Path>) {
    @Suppress("DialogTitleCapitalization")
    runBackgroundableTask(DiagnosticBundle.message("defender.config.progress"), project, false) {
      val success = checker.excludeProjectPaths(paths)
      if (success) {
        checker.ignoreStatusCheck(project, true)
        notification(DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
          .notify(project)
      }
      else {
        notification(DiagnosticBundle.message("defender.config.failed"), NotificationType.WARNING)
          .addAction(ShowLogAction.notificationAction())
          .notify(project)
      }
    }
  }

  private fun suppressCheck(checker: WindowsDefenderChecker, project: Project?) {
    checker.ignoreStatusCheck(project, true)
    val action = ActionsBundle.message("action.ResetWindowsDefenderNotification.text")
    notification(DiagnosticBundle.message("defender.config.restore", action), NotificationType.INFORMATION)
      .notify(project)
  }

  private fun notification(@NlsContexts.NotificationContent content: String, type: NotificationType): Notification =
    Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, type)
}
