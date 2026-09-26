// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import io.opentelemetry.api.metrics.BatchCallback
import java.util.concurrent.TimeUnit

/**
 * Reports {@link com.intellij.openapi.vfs.DiskQueryRelay} values to OTel.
 */
internal class DiskQueryRelayToOTelReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<ReportingService>()
  }

  @Service(Service.Level.APP)
  private class ReportingService : Disposable {

    var callback: BatchCallback? = null

    init {
      val otelMeter = getInstance().getMeter(PlatformMetrics)

      val taskExecutionTimeUs =
        otelMeter.counterBuilder("DiskQueryRelay.taskExecutionTotalTimeUs").buildObserver()
      val taskWaitingTimeUs =
        otelMeter.counterBuilder("DiskQueryRelay.taskWaitingTotalTimeUs").buildObserver()
      val tasksExecuted =
        otelMeter.counterBuilder("DiskQueryRelay.tasksExecuted").buildObserver()
      val tasksRequested =
        otelMeter.counterBuilder("DiskQueryRelay.tasksRequested").buildObserver()

      callback = otelMeter.batchCallback(
        Runnable {
          taskExecutionTimeUs.record(DiskQueryRelay.taskExecutionTotalTime(TimeUnit.MICROSECONDS))
          taskWaitingTimeUs.record(DiskQueryRelay.taskWaitingTotalTime(TimeUnit.MICROSECONDS))
          tasksExecuted.record(DiskQueryRelay.tasksExecuted().toLong())
          tasksRequested.record(DiskQueryRelay.tasksRequested().toLong())
        },
        taskExecutionTimeUs, taskWaitingTimeUs,
        tasksExecuted, tasksRequested
      )
    }

    override fun dispose() {
      callback?.close()
    }

  }
}
