// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.WindowsDefenderChecker.Extension
import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

internal object WindowsDefenderExcludeUtil {
  const val NOTIFICATION_GROUP = "WindowsDefender"
  private val defenderExclusions = ConcurrentHashMap<Path, Boolean>()

  fun markPathAsShownDefender(path: Path) {
    defenderExclusions.putIfAbsent(path, false)
  }

  fun isDefenderShown(path: Path): Boolean {
    return defenderExclusions.containsKey(path)
  }

  fun addPathsToExclude(paths: List<Path>) {
    paths.forEach { defenderExclusions.put(it, true) }
  }

  fun getPathsToExclude(): List<Path> {
    return defenderExclusions.filterValues { it }.keys.toImmutableList()
  }

  fun clearPathsToExclude() {
    defenderExclusions.replaceAll { _, _ -> false }
  }

  fun updateDefenderConfig(checker: WindowsDefenderChecker, project: Project, paths: List<Path>, isUpdatedFromNotification: Boolean = false, afterFinish: () -> Unit = {}) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      @Suppress("DialogTitleCapitalization")
      withBackgroundProgress(project, DiagnosticBundle.message("defender.config.progress"), false) {
        val success = checker.excludeProjectPaths(project, paths)
        if (success) {
          Notification(NOTIFICATION_GROUP, DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
            .notify(project)
        }
        else {
          Notification(NOTIFICATION_GROUP, DiagnosticBundle.message("defender.config.failed"), NotificationType.ERROR)
            .addAction(ShowLogAction.notificationAction())
            .notify(project)
        }
        afterFinish()
        WindowsDefenderStatisticsCollector.configured(project, success)
      }
    }
    if (isUpdatedFromNotification) WindowsDefenderStatisticsCollector.auto(project)
  }

  fun getPathsToExclude(project: Project?, projectPath: Path?): List<Path> {
    val paths = mutableListOf<Path>()
    paths.add(PathManager.getSystemDir())
    val epName: ExtensionPointName<Extension> = ExtensionPointName.create("com.intellij.defender.config")
    epName.forEachExtensionSafe { ext ->
      paths.addAll(ext.getPaths(project, projectPath));
    }
    return paths
  }
}