// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimple
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi

private val LOG = logger<WindowsDefenderCheckerActivity>()

internal class WindowsDefenderCheckerActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isUnitTestMode || !Registry.`is`("ide.check.windows.defender.rules", false)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val checker = serviceAsync<WindowsDefenderChecker>()
    if (pathsToExclude.isNotEmpty()) {
      WindowsDefenderExcludeUtil.updateDefenderConfig(checker, project, pathsToExclude)
      pathsToExclude.clear()
    }
    if (checker.isStatusCheckIgnored(project)) {
      LOG.info("status check is disabled")
      WindowsDefenderStatisticsCollector.protectionCheckSkipped(project)
      return
    }

    @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
    computeDetached {
      checkDefenderStatus(project, checker)
    }
  }

  private fun checkDefenderStatus(project: Project, checker: WindowsDefenderChecker) {
    val protection = checker.isRealTimeProtectionEnabled()
    WindowsDefenderStatisticsCollector.protectionCheckStatus(project, protection)
    if (protection != true) {
      LOG.info("real-time protection: ${protection}")
      return
    }

    val paths = checker.filterDevDrivePaths(checker.getPathsToExclude(project))
    if (paths.isEmpty()) {
      LOG.info("all paths are on a DevDrive")
      return
    }

    val pathList = paths.joinToString(separator = "<br>&nbsp;&nbsp;", prefix = "<br>&nbsp;&nbsp;") { it.toString() }
    val auto = DiagnosticBundle.message("exclude.folders")
    val manual = DiagnosticBundle.message("defender.config.manual")
    WindowsDefenderExcludeUtil.notification(DiagnosticBundle.message("defender.config.prompt", pathList, auto, manual), NotificationType.INFORMATION)
      .addAction(createSimpleExpiring(auto) { WindowsDefenderExcludeUtil.updateDefenderConfig(checker, project, paths) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress1")) { suppressCheck(checker, project, globally = false) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress2")) { suppressCheck(checker, project, globally = true) })
      .addAction(Separator.getInstance())
      .addAction(createSimple(manual) { showInstructions(checker, project) })
      .setSuggestionType(true)
      .setImportantSuggestion(true)
      .apply { collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST }
      .notify(project)
  }

  private fun showInstructions(checker: WindowsDefenderChecker, project: Project) {
    BrowserUtil.browse(checker.configurationInstructionsUrl)
    WindowsDefenderStatisticsCollector.manual(project)
  }

  private fun suppressCheck(checker: WindowsDefenderChecker, project: Project, globally: Boolean) {
    checker.ignoreStatusCheck(if (globally) null else project, true)
    val action = ActionsBundle.message("action.ResetWindowsDefenderNotification.text")
    WindowsDefenderExcludeUtil.notification(DiagnosticBundle.message("defender.config.restore", action), NotificationType.INFORMATION)
      .notify(project)
    WindowsDefenderStatisticsCollector.suppressed(project, globally)
  }
}
