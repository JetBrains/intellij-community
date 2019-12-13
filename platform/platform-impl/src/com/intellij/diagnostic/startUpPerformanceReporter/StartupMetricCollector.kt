// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.util.concurrent.atomic.AtomicBoolean

// todo `com.intellij.internal.statistic` package should be moved out of platform-impl module to own,
// and then this will be class moved to corresponding `intellij.platform.diagnostic` module
internal class StartupMetricCollector : StartupActivity {
  private var wasReported = AtomicBoolean(ApplicationManager.getApplication().isUnitTestMode)

  override fun runActivity(project: Project) {
    if (!wasReported.compareAndSet(false, true)) {
      return
    }

    val metrics = StartUpPerformanceService.getInstance().metrics ?: return
    val usageLogger = FUCounterUsageLogger.getInstance()
    metrics.forEachEntry { name, value ->
      val usageData = FeatureUsageData()
      usageData.addData("duration", value)
      var eventId = name
      if (eventId == "app initialization") {
        eventId = "appInit"
      }
      usageLogger.logEvent("startup", eventId, usageData)
      true
    }
  }
}