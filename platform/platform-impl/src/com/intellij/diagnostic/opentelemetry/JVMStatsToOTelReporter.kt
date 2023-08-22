// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.diagnostic.telemetry.JVM
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.metrics.BatchCallback
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Reports basic JVM stats into OTel.Metrics.
 * Currently reported: heap & direct memory usage. Feel free to add more.
 */
private class JVMStatsToOTelReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<ReportingService>()
  }

  @Service(Service.Level.APP)
  private class ReportingService : Disposable {
    private var batchCallback: BatchCallback? = null

    init {
      val otelMeter = TelemetryManager.getMeter(JVM)

      val usedHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.usedHeapBytes").ofLongs().buildObserver()
      val maxHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.maxHeapBytes").ofLongs().buildObserver()

      val usedNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.usedNativeBytes").ofLongs().buildObserver()
      val maxNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.maxNativeBytes").ofLongs().buildObserver()

      val threadCountGauge = otelMeter.gaugeBuilder("JVM.threadCount").ofLongs().buildObserver()

      val osLoadAverageGauge = otelMeter.gaugeBuilder("OS.loadAverage").buildObserver()

      val gcCollectionsCounter = otelMeter.counterBuilder("JVM.GC.collections").buildObserver()
      val gcCollectionTimesCounterMs = otelMeter.counterBuilder("JVM.GC.collectionTimesMs").buildObserver()

      val totalCpuTimeCounterMs = otelMeter.counterBuilder("JVM.totalCpuTimeMs").buildObserver()

      val memoryMXBean = ManagementFactory.getMemoryMXBean()
      val threadMXBean = ManagementFactory.getThreadMXBean()
      val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

      val osMXBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

      batchCallback = otelMeter.batchCallback(
        {
          val heapUsage = memoryMXBean.heapMemoryUsage
          //It seems like nonHeapMemoryUsage is unrelated to DirectByteBuffers usage -- that we're mostly interested in:
          val nonHeapUsage = memoryMXBean.nonHeapMemoryUsage

          usedHeapMemoryGauge.record(heapUsage.used)
          maxHeapMemoryGauge.record(heapUsage.max)

          usedNativeMemoryGauge.record(nonHeapUsage.used)
          maxNativeMemoryGauge.record(nonHeapUsage.max)

          threadCountGauge.record(threadMXBean.threadCount.toLong())

          osLoadAverageGauge.record(osMXBean.systemLoadAverage)

          var gcCount: Long = 0
          var gcTimeMs: Long = 0
          for (gcBean in gcBeans) {
            gcCount += gcBean.collectionCount
            gcTimeMs += gcBean.collectionTime
          }
          gcCollectionsCounter.record(gcCount)
          gcCollectionTimesCounterMs.record(gcTimeMs)

          val processCpuTimeNs = osMXBean.processCpuTime
          if (processCpuTimeNs != -1L) {
            totalCpuTimeCounterMs.record(NANOSECONDS.toMillis(processCpuTimeNs))
          }
        },

        usedHeapMemoryGauge, maxHeapMemoryGauge,
        usedNativeMemoryGauge, maxNativeMemoryGauge,
        threadCountGauge,
        osLoadAverageGauge,
        gcCollectionsCounter, gcCollectionTimesCounterMs,
        totalCpuTimeCounterMs
      )
    }

    override fun dispose() {
      batchCallback?.close()
    }
  }
}
