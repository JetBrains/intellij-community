// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.diagnostic.telemetry.JVM
import com.intellij.diagnostic.telemetry.TraceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.opentelemetry.api.metrics.BatchCallback
import java.lang.management.ManagementFactory

/**
 * Reports basic JVM stats into OTel.Metrics.
 * Currently reported: heap & direct memory usage. Feel free to add more.
 */
class JVMStatsToOTelReporter : ProjectActivity {

  override suspend fun execute(project: Project) {
    service<ReportingService>()
  }

  @Service(Service.Level.APP)
  private class ReportingService : Disposable {
    private var batchCallback: BatchCallback? = null

    init {
      val otelMeter = TraceManager.getMeter(JVM)

      val usedHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.usedHeapBytes").ofLongs().buildObserver()
      val maxHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.maxHeapBytes").ofLongs().buildObserver()

      val usedNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.usedNativeBytes").ofLongs().buildObserver()
      val maxNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.maxNativeBytes").ofLongs().buildObserver()

      val threadCountGauge = otelMeter.gaugeBuilder("JVM.threadCount").ofLongs().buildObserver()

      val memoryMXBean = ManagementFactory.getMemoryMXBean()
      val threadMXBean = ManagementFactory.getThreadMXBean()

      batchCallback = otelMeter.batchCallback(
        {
          val heapUsage = memoryMXBean.heapMemoryUsage
          val nonHeapUsage = memoryMXBean.nonHeapMemoryUsage

          usedHeapMemoryGauge.record(heapUsage.used)
          maxHeapMemoryGauge.record(heapUsage.max)

          usedNativeMemoryGauge.record(nonHeapUsage.used)
          maxNativeMemoryGauge.record(nonHeapUsage.max)

          threadCountGauge.record(threadMXBean.threadCount.toLong())
        },
        usedHeapMemoryGauge, maxHeapMemoryGauge,
        usedNativeMemoryGauge, maxNativeMemoryGauge,
        threadCountGauge
      )
    }

    override fun dispose() {
      batchCallback?.close()
    }
  }
}
