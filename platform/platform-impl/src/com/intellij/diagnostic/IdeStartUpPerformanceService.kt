// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

private class IdeStartUpPerformanceService(coroutineScope: CoroutineScope) : StartUpPerformanceReporter(coroutineScope) {
  override fun addActivityListener(project: Project) {
    if (perfFilePath == null) {
      super.addActivityListener(project)
      return
    }

    val projectName = project.name
    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        logStats(projectName)
      }
    })
  }
}