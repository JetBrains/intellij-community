// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimple
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.nio.file.Path

internal class WindowsDefenderCheckerActivity : ProjectActivity {
  @Suppress("CompanionObjectInExtension")
  companion object {
    private val LOG = logger<WindowsDefenderCheckerActivity>()

    fun runAndNotify(project: Project?, process: () -> Boolean) {
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        val success = if (project != null) {
          @Suppress("DialogTitleCapitalization")
          withBackgroundProgress(project, DiagnosticBundle.message("defender.config.progress"), cancellable = false) {
            process()
          }
        }
        else {
          process()
        }

        WindowsDefenderStatisticsCollector.configured(project, success)

        if (success) {
          Notification("WindowsDefender", DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
            .notify(project)
        }
        else {
          Notification("WindowsDefender", DiagnosticBundle.message("defender.config.failed"), NotificationType.ERROR)
            .addAction(ShowLogAction.notificationAction())
            .notify(project)
        }
      }
    }
  }

  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isUnitTestMode || !Registry.`is`("ide.check.windows.defender.rules", false)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val checker = serviceAsync<WindowsDefenderChecker>()
    if (checker.isStatusCheckIgnored(project) || checker.isAlreadyProcessed(project)) {
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

    val projectDir = project.guessProjectDir()?.let { it.fileSystem.getNioPath(it) }
    if (projectDir != null && checker.isUnderDownloads(projectDir)) {
      LOG.info("under downloads: ${projectDir}")
      return
    }

    val paths = checker.filterDevDrivePaths(checker.getPathsToExclude(project))
    if (paths.isEmpty()) {
      LOG.info("all paths are on a DevDrive")
      return
    }

    val pathList = paths.joinToString(separator = "<br>&nbsp;&nbsp;", prefix = "<br>&nbsp;&nbsp;")
    val auto = DiagnosticBundle.message("exclude.folders")
    val manual = DiagnosticBundle.message("defender.config.manual")
    val content = DiagnosticBundle.message("defender.config.prompt", pathList, auto, manual)
    Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, NotificationType.INFORMATION)
      .addAction(createSimpleExpiring(auto) { updateDefenderConfig(checker, project, paths) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress1")) { suppressCheck(checker, project, globally = false) })
      .addAction(createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress2")) { suppressCheck(checker, project, globally = true) })
      .addAction(Separator.getInstance())
      .addAction(createSimple(manual) { showInstructions(checker, project) })
      .setSuggestionType(true)
      .setImportantSuggestion(true)
      .apply { collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST }
      .notify(project)
  }

  private fun updateDefenderConfig(checker: WindowsDefenderChecker, project: Project, paths: List<Path>) {
    runAndNotify(project) { checker.excludeProjectPaths(project, paths) }
    WindowsDefenderStatisticsCollector.auto(project)
  }

  private fun showInstructions(checker: WindowsDefenderChecker, project: Project) {
    BrowserUtil.browse(checker.configurationInstructionsUrl)
    WindowsDefenderStatisticsCollector.manual(project)
  }

  private fun suppressCheck(checker: WindowsDefenderChecker, project: Project, globally: Boolean) {
    checker.ignoreStatusCheck(if (globally) null else project, true)
    WindowsDefenderStatisticsCollector.suppressed(project, globally)
  }
}
