// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val isDebugEnabled = DebugAttachDetector.isDebugEnabled()
private const val TOLERABLE_UI_LATENCY = 100
private const val UI_RESPONSE_LOGGING_INTERVAL_MS = 100000

private class FusFreezeReporter : PerformanceListener {
  @Volatile
  private var previousLoggedUiResponse: Long = 0

  override fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
    if (!isDebugEnabled) {
      LifecycleUsageTriggerCollector.onFreeze(durationMs)
    }
  }

  override fun uiResponded(latencyMs: Long) {
    val currentTime = System.nanoTime()
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(currentTime - previousLoggedUiResponse)
    if (elapsedMs >= UI_RESPONSE_LOGGING_INTERVAL_MS) {
      previousLoggedUiResponse = currentTime
      UILatencyLogger.LATENCY.log(latencyMs)
    }
    if (latencyMs >= TOLERABLE_UI_LATENCY && !isDebugEnabled) {
      val hasIndexingGoingOn = ProjectManager.getInstance().openProjects.any { DumbService.isDumb(it) }
      UILatencyLogger.LAGGING.log(latencyMs, hasIndexingGoingOn)
    }
  }
}
