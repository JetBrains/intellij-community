// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.CoroutineScope

private class IdeStartUpPerformanceService(coroutineScope: CoroutineScope) : StartUpPerformanceReporter(coroutineScope) {
  @Volatile
  private var editorRestoringTillHighlighted = false

  @Volatile
  private var projectOpenedActivitiesPassed = false

  init {
    if (perfFilePath != null) {
      val projectName = ProjectManagerEx.getOpenProjects().firstOrNull()?.name ?: "unknown"
      ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          logStats(projectName)
        }
      })
    }
  }

  override fun projectDumbAwareActivitiesFinished() {
    projectOpenedActivitiesPassed = true
    if (editorRestoringTillHighlighted) {
      completed()
    }
  }

  override fun editorRestoringTillHighlighted() {
    editorRestoringTillHighlighted = true
    if (projectOpenedActivitiesPassed) {
      completed()
    }
  }

  private fun completed() {
    if (perfFilePath != null) {
      return
    }

    StartUpMeasurer.stopPluginCostMeasurement()
    // don't report statistic from here if we want to measure project import duration
    if (!java.lang.Boolean.getBoolean("idea.collect.project.import.performance")) {
      keepAndLogStats(ProjectManagerEx.getOpenProjects().firstOrNull()?.name ?: "unknown")
    }
  }
}