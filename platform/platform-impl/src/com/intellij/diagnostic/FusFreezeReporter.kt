// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.SystemProperties
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val UI_RESPONSE_LOGGING_INTERVAL_MS = 100000

internal class FusFreezeReporter : FreezeListener {
  @Volatile
  private var previousLoggedUiResponse: Long = 0

  private val isDebugEnabled = DebugAttachDetector.isDebugEnabled()
  private val TOLERABLE_UI_LATENCY = SystemProperties.getIntProperty("fus.freeze.tolerable.ui.latency", 100)

  override suspend fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
    if (!isDebugEnabled) {
      LifecycleUsageTriggerCollector.onFreeze(durationMs)
    }
  }

  override suspend fun uiResponded(uiLagData: FreezeListener.UiLagData) {
    val currentTime = System.nanoTime()
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(currentTime - previousLoggedUiResponse)
    if (elapsedMs >= UI_RESPONSE_LOGGING_INTERVAL_MS) {
      previousLoggedUiResponse = currentTime
      UILatencyLogger.logLatency(uiLagData.latencyMs)
    }
    if (uiLagData.latencyMs >= TOLERABLE_UI_LATENCY && !isDebugEnabled) {
      val hasIndexingGoingOn = readActionBlocking {
        ProjectManager.getInstance().openProjects
          .any { DumbService.isDumb(it) }
      }
      UILatencyLogger.logLagging(uiLagData.latencyMs, hasIndexingGoingOn, uiLagData.wasFreezePopupShown)
    }
  }
}
