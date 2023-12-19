// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.diagnostic.telemetry.JVM
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.util.io.IOUtil
import com.sun.management.ThreadMXBean
import io.opentelemetry.api.metrics.BatchCallback
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Reports JVM-wide metrics into OTel.Metrics.
 * Currently reported: heap & direct memory usage, threads count, GC times, JVM/OS CPU consumption.
 * Feel free to add more.
 */
private class JVMStatsToOTelReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<ReportingService>()
  }

  @Service(Service.Level.APP)
  private class ReportingService {
    private var batchCallback: BatchCallback? = null

    init {
      val otelMeter = TelemetryManager.getMeter(JVM)

      val usedHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.usedHeapBytes").ofLongs().buildObserver()
      val maxHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.maxHeapBytes").ofLongs().buildObserver()

      //Off-heap memory used by JVM structures
      val usedNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.usedNativeBytes").ofLongs().buildObserver()
      //Off-heap memory used by direct ByteBuffers
      val totalDirectByteBuffersGauge = otelMeter.gaugeBuilder("JVM.totalDirectByteBuffersBytes").ofLongs().buildObserver()

      val threadCountGauge = otelMeter.gaugeBuilder("JVM.threadCount").ofLongs().buildObserver()
      val threadCountMaxGauge = otelMeter.gaugeBuilder("JVM.maxThreadCount").ofLongs().buildObserver()

      val osLoadAverageGauge = otelMeter.gaugeBuilder("OS.loadAverage").buildObserver()

      val gcCollectionsCounter = otelMeter.counterBuilder("JVM.GC.collections").buildObserver()
      val gcCollectionTimesCounterMs = otelMeter.counterBuilder("JVM.GC.collectionTimesMs").buildObserver()
      val totalBytesAllocatedCounter = otelMeter.counterBuilder("JVM.totalBytesAllocated").buildObserver()

      val totalCpuTimeCounterMs = otelMeter.counterBuilder("JVM.totalCpuTimeMs").buildObserver()

      val memoryMXBean = ManagementFactory.getMemoryMXBean()
      val threadMXBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
      val allocatedMemoryProvider = AllocatedMemoryProvider(threadMXBean)

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
          totalDirectByteBuffersGauge.record(IOUtil.directBuffersTotalAllocatedSize())

          threadCountGauge.record(threadMXBean.threadCount.toLong())
          threadCountMaxGauge.record(threadMXBean.peakThreadCount.toLong())

          osLoadAverageGauge.record(osMXBean.systemLoadAverage)

          var gcCount: Long = 0
          var gcTimeMs: Long = 0
          for (gcBean in gcBeans) {
            gcCount += gcBean.collectionCount
            gcTimeMs += gcBean.collectionTime
          }
          gcCollectionsCounter.record(gcCount)
          gcCollectionTimesCounterMs.record(gcTimeMs)

          if (allocatedMemoryProvider.isAvailable()) {
            totalBytesAllocatedCounter.record(allocatedMemoryProvider.totalBytesAllocatedSinceStartup())
          }

          val processCpuTimeNs = osMXBean.processCpuTime
          if (processCpuTimeNs != -1L) {
            totalCpuTimeCounterMs.record(NANOSECONDS.toMillis(processCpuTimeNs))
          }
        },

        usedHeapMemoryGauge, maxHeapMemoryGauge,
        usedNativeMemoryGauge, totalDirectByteBuffersGauge,
        threadCountGauge,
        threadCountMaxGauge,
        osLoadAverageGauge,
        gcCollectionsCounter, gcCollectionTimesCounterMs,
        totalBytesAllocatedCounter,
        totalCpuTimeCounterMs
      )

      //We intentionally don't unregister batchCallback registered above -- because we register OTel.shutdown()
      // in a ShutDownTracker, and expect it to squeeze the last reading from all Metrics registered at the very
      // end of app lifecycle. If we unregister the callback here -- it prevents this batchCallback from participate
      // in that last reading. It doesn't matter much for real-life app runs (which usually hours long, so single
      // last reading is not that important) -- but it seems like we need this last reading of JVM stats for the
      // performance dashboard?
    }
  }

  /**
   * JMX bean provides per-thread allocations -- but threads come and go, and with thread termination all the
   * memory allocated by it also disappears from data reported by [ThreadMXBean]. To keep the total allocated
   * memory metric continuous, we need to compensate for that disappearing 'memory allocated by threads now dead'.
   * The class keeps track of the threads alive/respective memory allocated by a thread, and if thread X disappears
   * from the current turn's [ThreadMXBean.getAllThreadIds] -- it's allocated memory is kept in
   * [totalBytesAllocatedByTerminatedThreads], and still contributes to the [totalBytesAllocatedSinceStartup]
   * reported by the class
   * There are still few sources of errors: e.g. we don't count the memory allocated by died thread since last
   * turn till the termination -- but it must be precise enough for monitoring purposes.
   */
  class AllocatedMemoryProvider(private val threadMXBean: ThreadMXBean) {

    /** Threads (ids) seen alive on previous call to [totalBytesAllocatedSinceStartup]*/
    private var previouslyAliveThreadsIds: LongArray = LongArray(0)
    private var allocatedByPreviouslyAliveThreads: Long2LongOpenHashMap = Long2LongOpenHashMap()

    /**
     * Total memory allocated by already terminated threads -- so this memory is not listed in current calls
     * to [ThreadMXBean.getThreadAllocatedBytes]
     */
    private var totalBytesAllocatedByTerminatedThreads: Long = 0L

    fun isAvailable() = threadMXBean.isThreadAllocatedMemorySupported && threadMXBean.isThreadAllocatedMemoryEnabled

    fun totalBytesAllocatedSinceStartup(): Long {
      if (!isAvailable()) {
        return 0L
      }

      val currentlyAliveThreadIds = threadMXBean.allThreadIds
      val perThreadAllocatedBytes = threadMXBean.getThreadAllocatedBytes(currentlyAliveThreadIds)

      val allocatedMemoryByThreadId = Long2LongOpenHashMap()
      currentlyAliveThreadIds.forEachIndexed { index, threadId ->
        allocatedMemoryByThreadId[threadId] = perThreadAllocatedBytes[index]
      }

      val bytesAllocatedByTerminatedThreads = previouslyAliveThreadsIds.sumOf { previousThreadId ->
        val previouslyAllocatedByThread = allocatedByPreviouslyAliveThreads[previousThreadId]
        val currentlyAllocatedByThread = allocatedMemoryByThreadId[previousThreadId]
        if (currentlyAllocatedByThread < previouslyAllocatedByThread) {
          //thread is terminated (currentlyAllocatedByThread=0), or it is terminated, but its threadId got reused,
          // (so allocated memory counter was restarted):
          return@sumOf previouslyAllocatedByThread
        }
        return@sumOf 0L
      }
      totalBytesAllocatedByTerminatedThreads += bytesAllocatedByTerminatedThreads
      val totalBytesAllocatedByAllThreads = perThreadAllocatedBytes.sum() + totalBytesAllocatedByTerminatedThreads


      previouslyAliveThreadsIds = currentlyAliveThreadIds
      allocatedByPreviouslyAliveThreads = allocatedMemoryByThreadId

      return totalBytesAllocatedByAllThreads
    }
  }
}
