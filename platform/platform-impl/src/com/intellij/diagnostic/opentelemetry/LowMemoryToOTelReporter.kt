// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.LowMemoryWatcherManager
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import io.opentelemetry.api.metrics.DoubleGauge
import io.opentelemetry.api.metrics.LongGauge

/**
 * Reports {@link com.intellij.openapi.util.LowMemoryWatcherManager} values to OTel
 *
 * (It would be much easier to have this monitoring embedded into the {@link com.intellij.openapi.util.LowMemoryWatcherManager}
 * itself, but the platform.util module lacks the necessary dependency on monitoring libs, hence for the platform.util components
 * we're forced to attach monitoring 'from outside')
 */
internal class LowMemoryToOTelReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<ReportingService>()
  }

  @Service(Service.Level.APP)
  private class ReportingService : LowMemoryWatcherManager.Listener {
    private val gcLoadGauge: DoubleGauge
    private val gcOverloaded: LongGauge

    init {
      val otelMeter = TelemetryManager.getMeter(PlatformMetrics)
      gcLoadGauge = otelMeter.gaugeBuilder("LowMemory.gcLoad").build()
      //RC: this boolean flag is logically better be an Attributes of the .gcLoad metric, but we don't support Attributes
      //    in our open-telemetry-metrics.csv/open-telemetry-metrics-plotter.html, so I introduce the dedicated metric instead:
      gcOverloaded = otelMeter.gaugeBuilder("LowMemory.gcOverloaded").ofLongs().build()
      (AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService).lowMemoryWatcherManager.addListener(this)
    }

    override fun memoryStatus(event: LowMemoryWatcherManager.LowMemoryEvent) {
      gcLoadGauge.set(event.gcLoadScore)
      gcOverloaded.set(if (event.gcOverloaded) 1 else 0)
    }
  }
}
