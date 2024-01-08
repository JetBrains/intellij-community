// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowLogAction
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimple
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import java.nio.file.Path

private val LOG = logger<WindowsDefenderCheckerActivity>()

internal class WindowsDefenderCheckerActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isUnitTestMode || !Registry.`is`("ide.check.windows.defender.rules", false)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val checker = WindowsDefenderChecker.getInstance()

    if (checker.isStatusCheckIgnored(project)) {
      LOG.info("status check is disabled")
      WindowsDefenderStatisticsCollector.protectionCheckSkipped(project)
      return
    }

    val protection = checker.isRealTimeProtectionEnabled
    WindowsDefenderStatisticsCollector.protectionCheckStatus(project, protection)
    if (protection != true) {
      LOG.info("real-time protection: ${protection}")
      return
    }

    val paths = checker.getPathsToExclude(project)
    val pathList = paths.joinToString(separator = "<br>&nbsp;&nbsp;", prefix = "<br>&nbsp;&nbsp;") { it.toString() }
    val auto = DiagnosticBundle.message("defender.config.auto")
    val manual = DiagnosticBundle.message("defender.config.manual")
    notification(DiagnosticBundle.message("defender.config.prompt", pathList, auto, manual), NotificationType.INFORMATION)
      .addAction(createSimpleExpiring(auto) { updateDefenderConfig(checker, project, paths) })
      .addAction(createSimple(manual) { showInstructions(checker, project) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress1")) { suppressCheck(checker, project, globally = false) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress2")) { suppressCheck(checker, project, globally = true) })
      .setSuggestionType(true)
      .setImportantSuggestion(true)
      .apply { collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST }
      .notify(project)
  }

  private fun updateDefenderConfig(checker: WindowsDefenderChecker, project: Project, paths: List<Path>) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      @Suppress("DialogTitleCapitalization")
      withBackgroundProgress(project, DiagnosticBundle.message("defender.config.progress"), false) {
        val success = checker.excludeProjectPaths(project, paths)
        if (success) {
          notification(DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
            .notify(project)
        }
        else {
          notification(DiagnosticBundle.message("defender.config.failed"), NotificationType.WARNING)
            .addAction(ShowLogAction.notificationAction())
            .notify(project)
        }
        WindowsDefenderStatisticsCollector.configured(project, success)
      }
    }
    WindowsDefenderStatisticsCollector.auto(project)
  }

  private fun showInstructions(checker: WindowsDefenderChecker, project: Project) {
    BrowserUtil.browse(checker.configurationInstructionsUrl)
    WindowsDefenderStatisticsCollector.manual(project)
  }

  private fun suppressCheck(checker: WindowsDefenderChecker, project: Project, globally: Boolean) {
    checker.ignoreStatusCheck(if (globally) null else project, true)
    val action = ActionsBundle.message("action.ResetWindowsDefenderNotification.text")
    notification(DiagnosticBundle.message("defender.config.restore", action), NotificationType.INFORMATION)
      .notify(project)
    WindowsDefenderStatisticsCollector.suppressed(project, globally)
  }

  private fun notification(@NlsContexts.NotificationContent content: String, type: NotificationType): Notification =
    Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, type)
}
