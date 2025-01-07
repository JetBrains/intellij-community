// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.diagnostic.PlatformMemoryUtil
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.diagnostic.telemetry.JVM
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.util.io.IOUtil
import com.sun.management.ThreadMXBean
import io.opentelemetry.api.metrics.BatchCallback
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
  private class ReportingService(cs: CoroutineScope) {
    private var batchCallback: BatchCallback? = null

    init {
      val otelMeter = TelemetryManager.getMeter(JVM)

      //OS-provided memory metrics
      val ramGauge = otelMeter.gaugeBuilder("MEM.ramBytes").ofLongs().buildObserver()
      val ramMinusFileMappingsGauge = otelMeter.gaugeBuilder("MEM.ramMinusFileMappingsBytes").ofLongs().buildObserver()
      val ramPlusSwapMinusFileMappingsGauge = otelMeter.gaugeBuilder("MEM.ramPlusSwapMinusFileMappingsBytes").ofLongs().buildObserver()
      val fileMappingsRamGauge = otelMeter.gaugeBuilder("MEM.fileMappingsRamBytes").ofLongs().buildObserver()

      val avgRamGauge = otelMeter.gaugeBuilder("MEM.avgRamBytes").ofLongs().buildObserver()
      val avgRamMinusFileMappingsGauge = otelMeter.gaugeBuilder("MEM.avgRamMinusFileMappingsBytes").ofLongs().buildObserver()
      val avgRamPlusSwapMinusFileMappingsGauge = otelMeter.gaugeBuilder("MEM.avgRamPlusSwapMinusFileMappingsBytes").ofLongs().buildObserver()
      val avgFileMappingsRamGauge = otelMeter.gaugeBuilder("MEM.avgFileMappingsRamBytes").ofLongs().buildObserver()
      val avgMemUsageProvider = if (ApplicationManagerEx.isInIntegrationTest()) {
        AvgMemoryUsageProvider(cs)
      } else {
        null
      }

      //JVM memory metrics
      val usedHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.usedHeapBytes").ofLongs().buildObserver()
      val committedHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.committedHeapBytes").ofLongs().buildObserver()
      val maxHeapMemoryGauge = otelMeter.gaugeBuilder("JVM.maxHeapBytes").ofLongs().buildObserver()

      //Off-heap memory used by JVM structures
      val usedNativeMemoryGauge = otelMeter.gaugeBuilder("JVM.usedNativeBytes").ofLongs().buildObserver()
      //Off-heap memory used by direct ByteBuffers
      val totalDirectByteBuffersGauge = otelMeter.gaugeBuilder("JVM.totalDirectByteBuffersBytes").ofLongs().buildObserver()

      val threadCountGauge = otelMeter.gaugeBuilder("JVM.threadCount").ofLongs().buildObserver()
      val threadCountMaxGauge = otelMeter.gaugeBuilder("JVM.maxThreadCount").ofLongs().buildObserver()
      val newThreadsCounter = otelMeter.counterBuilder("JVM.newThreadsCount").buildObserver()

      val osLoadAverageGauge = otelMeter.gaugeBuilder("OS.loadAverage").buildObserver()

      val gcCollectionsCounter = otelMeter.counterBuilder("JVM.GC.collections").buildObserver()
      val gcCollectionTimesCounterMs = otelMeter.counterBuilder("JVM.GC.collectionTimesMs").buildObserver()
      val totalBytesAllocatedCounter = otelMeter.counterBuilder("JVM.totalBytesAllocated").buildObserver()

      val totalCpuTimeCounterMs = otelMeter.counterBuilder("JVM.totalCpuTimeMs").buildObserver()

      val totalSafepointCounter = otelMeter.counterBuilder("JVM.totalSafepointCount").buildObserver()
      val totalTimeAtSafepointsCounterMs = otelMeter.counterBuilder("JVM.totalTimeAtSafepointsMs").buildObserver()
      val totalTimeToSafepointsCounterMs = otelMeter.counterBuilder("JVM.totalTimeToSafepointsMs").buildObserver()


      val memoryMXBean = ManagementFactory.getMemoryMXBean()
      val threadMXBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
      val allocatedMemoryProvider = AllocatedMemoryProvider(threadMXBean)

      val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

      val osMXBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

      val safepointBean = SafepointBean


      batchCallback = otelMeter.batchCallback(
        {
          val memStats = PlatformMemoryUtil.getInstance().getCurrentProcessMemoryStats()
          if (memStats != null) {
            ramGauge.record(memStats.ram)
            ramMinusFileMappingsGauge.record(memStats.ramMinusFileMappings)
            ramPlusSwapMinusFileMappingsGauge.record(memStats.ramPlusSwapMinusFileMappings)
            fileMappingsRamGauge.record(memStats.fileMappingsRam)
          }
          val avgMemStats = avgMemUsageProvider?.getAvgCurrentProcessMemoryStats()
          if (avgMemStats != null) {
            avgRamGauge.record(avgMemStats.ram)
            avgRamMinusFileMappingsGauge.record(avgMemStats.ramMinusFileMappings)
            avgRamPlusSwapMinusFileMappingsGauge.record(avgMemStats.ramPlusSwapMinusFileMappings)
            avgFileMappingsRamGauge.record(avgMemStats.fileMappingsRam)
          }

          //Memory (heap/off-heap):
          val heapUsage = memoryMXBean.heapMemoryUsage
          //It seems like nonHeapMemoryUsage is unrelated to DirectByteBuffers usage -- that we're mostly interested in:
          val nonHeapUsage = memoryMXBean.nonHeapMemoryUsage

          usedHeapMemoryGauge.record(heapUsage.used)
          committedHeapMemoryGauge.record(heapUsage.committed)
          maxHeapMemoryGauge.record(heapUsage.max)

          usedNativeMemoryGauge.record(nonHeapUsage.used)
          totalDirectByteBuffersGauge.record(IOUtil.directBuffersTotalAllocatedSize())

          //Threads:
          threadCountGauge.record(threadMXBean.threadCount.toLong())
          threadCountMaxGauge.record(threadMXBean.peakThreadCount.toLong())
          newThreadsCounter.record(threadMXBean.totalStartedThreadCount)

          //GC:
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

          //JVM safepoints:
          totalSafepointCounter.record(safepointBean.safepointCount() ?: -1)
          totalTimeToSafepointsCounterMs.record(safepointBean.totalTimeToSafepointMs() ?: -1)
          totalTimeAtSafepointsCounterMs.record(safepointBean.totalTimeAtSafepointMs() ?: -1)

          //OS/process load:
          osLoadAverageGauge.record(osMXBean.systemLoadAverage)

          val processCpuTimeNs = osMXBean.processCpuTime
          if (processCpuTimeNs != -1L) {
            totalCpuTimeCounterMs.record(NANOSECONDS.toMillis(processCpuTimeNs))
          }
        },

        ramGauge, ramMinusFileMappingsGauge, ramPlusSwapMinusFileMappingsGauge, fileMappingsRamGauge,
        avgRamGauge, avgRamMinusFileMappingsGauge, avgRamPlusSwapMinusFileMappingsGauge, avgFileMappingsRamGauge,

        usedHeapMemoryGauge, committedHeapMemoryGauge, maxHeapMemoryGauge,
        usedNativeMemoryGauge, totalDirectByteBuffersGauge,

        threadCountGauge, threadCountMaxGauge, newThreadsCounter,

        gcCollectionsCounter, gcCollectionTimesCounterMs,
        totalBytesAllocatedCounter,

        totalCpuTimeCounterMs, osLoadAverageGauge,

        totalSafepointCounter, totalTimeToSafepointsCounterMs, totalTimeAtSafepointsCounterMs
      )

      //We intentionally don't unregister batchCallback registered above -- because we register OTel.shutdown()
      // in a ShutDownTracker, and expect it to squeeze the last reading from all Metrics registered at the very
      // end of app lifecycle. If we unregister the callback here -- it prevents this batchCallback from participate
      // in that last reading. It doesn't matter much for real-life app runs (which usually hours long, so single
      // last reading is not that important) -- but it seems like we need this last reading of JVM stats for the
      // performance dashboard?
    }
  }

  private class AvgMemoryUsageProvider(cs: CoroutineScope) {
    @Volatile
    private var counters: Counters = Counters()

    init {
      cs.launch(CoroutineName("JVMStatsToOTelReporter.AvgMemoryUsageProvider")) {
        while (isActive) {
          val memStats = PlatformMemoryUtil.getInstance().getCurrentProcessMemoryStats()
          if (memStats != null) {
            val prev = counters
            counters = Counters(
              ram = prev.ram.add(memStats.ram / 1024),
              ramMinusFileMappings = prev.ramMinusFileMappings.add(memStats.ramMinusFileMappings / 1024),
              ramPlusSwapMinusFileMappings = prev.ramPlusSwapMinusFileMappings.add(memStats.ramPlusSwapMinusFileMappings / 1024),
              fileMappingsRam = prev.fileMappingsRam.add(memStats.fileMappingsRam / 1024),
            )
          }
          delay(100)
        }
      }
    }

    fun getAvgCurrentProcessMemoryStats(): PlatformMemoryUtil.MemoryStats? {
      if (counters.ram.getAvg() == 0L) return null

      return PlatformMemoryUtil.MemoryStats(
        ram = counters.ram.getAvg() * 1024,
        ramMinusFileMappings = counters.ramMinusFileMappings.getAvg() * 1024,
        ramPlusSwapMinusFileMappings = counters.ramPlusSwapMinusFileMappings.getAvg() * 1024,
        fileMappingsRam = counters.fileMappingsRam.getAvg() * 1024,
      )
    }

    private class Counters(
      val ram: AvgCounter = AvgCounter(),
      val ramMinusFileMappings: AvgCounter = AvgCounter(),
      val ramPlusSwapMinusFileMappings: AvgCounter = AvgCounter(),
      val fileMappingsRam: AvgCounter = AvgCounter(),
    )

    private class AvgCounter(
      private val count: Long = 0,
      private val total: Long = 0,
    ) {
      fun add(value: Long): AvgCounter {
        if (total == Long.MAX_VALUE) return this

        val newTotal = try {
          Math.addExact(total, value)
        } catch (_: ArithmeticException) {
          Long.MAX_VALUE
        }

        return AvgCounter(count + 1, newTotal)
      }

      fun getAvg(): Long = if (count == 0L) 0 else total / count
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

/**
 * Uses [sun.management.HotspotRuntimeMBean] got from [sun.management.ManagementFactoryHelper].
 * Thanks to Vadim Salavatov
 *
 * Requires (see OpenedPackages.txt)
 * ```
 * --add-exports=java.management/sun.management=ALL-UNNAMED
 * --add-opens=java.management/sun.management=ALL-UNNAMED
 * ```
 */
internal object SafepointBean {
  private val getSafepointCountHandle: () -> Long?
  private val getTotalSafepointTimeHandle: () -> Long?
  private val getSafepointSyncTimeHandle: () -> Long?

  init {
    //type: sun.management.HotspotRuntimeMBean
    val hotspotRuntimeMBean: Any? = try {
      val clazz = Class.forName("sun.management.ManagementFactoryHelper")
      clazz.getMethod("getHotspotRuntimeMBean").invoke(null)!!
    }
    catch (t: Throwable) {
      currentClassLogger().warn("Can't get HotspotRuntimeMBean", t)
      null
    }

    /** @return method call wrapped in lambda. Lambda return null if method call fails*/
    fun wrapMethodCall(methodName: String): () -> Long? {
      try {
        val method = hotspotRuntimeMBean!!.javaClass.getMethod(methodName)
        method.isAccessible = true
        method.invoke(hotspotRuntimeMBean) // test if works (=if supported at all)

        return { method.invoke(hotspotRuntimeMBean) as Long }
      }
      catch (_: Throwable) {
        //if method call fails right away => likely, method/bean is not supported
        //   => don't call it again, reduce an overhead:
        return { null }
      }
    }

    getSafepointCountHandle = wrapMethodCall("getSafepointCount")
    getTotalSafepointTimeHandle = wrapMethodCall("getTotalSafepointTime")
    getSafepointSyncTimeHandle = wrapMethodCall("getSafepointSyncTime")
  }

  /** @return the number of safepoints taken place since the JVM start. */
  fun safepointCount(): Long? = getSafepointCountHandle()

  /** @return the accumulated time spent _at_ safepoints (milliseconds), since JVM start */
  fun totalTimeAtSafepointMs(): Long? = getTotalSafepointTimeHandle()

  /** @return the accumulated time spent _getting to_ safepoints (milliseconds), since JVM start */
  fun totalTimeToSafepointMs(): Long? = getSafepointSyncTimeHandle()
}
