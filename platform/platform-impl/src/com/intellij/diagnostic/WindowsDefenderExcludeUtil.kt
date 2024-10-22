// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rd.util.collections.SynchronizedList
import kotlinx.coroutines.launch
import java.nio.file.Path

internal object WindowsDefenderExcludeUtil {

  fun updateDefenderConfig(checker: WindowsDefenderChecker, project: Project, paths: List<Path>, onSuccess: () -> Unit = {}) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      @Suppress("DialogTitleCapitalization")
      withBackgroundProgress(project, DiagnosticBundle.message("defender.config.progress"), false) {
        val success = checker.excludeProjectPaths(project, paths)
        if (success) {
          notification(DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
            .notify(project)
          onSuccess()
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

  internal fun notification(@NlsContexts.NotificationContent content: String, type: NotificationType): Notification =
    Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, type)
}

internal val pathsToExclude = SynchronizedList<Path>()