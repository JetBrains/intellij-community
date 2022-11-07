// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Service(Service.Level.APP)
internal class IdeHeartbeatEventReporter : Disposable {
  private val executor: ScheduledExecutorService?
  private val thread: ScheduledFuture<*>?
  private var lastCpuTime: Long = -1
  private var lastGcTime: Long = -1
  private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

  init {
    executor = AppExecutorUtil.createBoundedScheduledExecutorService("IDE Heartbeat", 1)
    thread = executor.scheduleWithFixedDelay(Runnable { recordHeartbeat() },
                                             Registry.intValue("ide.heartbeat.delay") /* don't execute during start-up */.toLong(),
                                             UI_RESPONSE_LOGGING_INTERVAL_MS.toLong(), TimeUnit.MILLISECONDS
    )
  }

  companion object {
    const val UI_RESPONSE_LOGGING_INTERVAL_MS = 100000
  }

  private fun recordHeartbeat() {
    val mxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    var systemCpuLoad = (mxBean.cpuLoad * 100).roundToInt()
    systemCpuLoad = if (systemCpuLoad >= 0) systemCpuLoad else -1
    val swapSize = mxBean.totalSwapSpaceSize.toDouble()
    val swapLoad = if (swapSize > 0) ((1 - mxBean.freeSwapSpaceSize / swapSize) * 100).toInt() else 0
    val totalGcTime = gcBeans.sumOf { it.collectionTime }
    val thisGcTime = if (lastGcTime == -1L) 0 else totalGcTime - lastGcTime
    lastGcTime = thisGcTime
    val totalCpuTime = mxBean.processCpuTime
    val thisCpuTime = if (totalCpuTime < 0 || lastCpuTime < 0) -1 else totalCpuTime - lastCpuTime
    lastCpuTime = thisCpuTime

    // don't report total GC time in the first 5 minutes of IJ execution
    UILatencyLogger.HEARTBEAT.log(
      UILatencyLogger.SYSTEM_CPU_LOAD.with(systemCpuLoad),
      UILatencyLogger.SWAP_LOAD.with(swapLoad),
      UILatencyLogger.CPU_TIME.with(TimeUnit.NANOSECONDS.toMillis(thisCpuTime).toInt()),
      UILatencyLogger.GC_TIME.with(thisGcTime.toInt())
    )
  }

  override fun dispose() {
    thread?.cancel(true)
    executor?.shutdownNow()
  }

  internal class Loader : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      service<IdeHeartbeatEventReporter>()
    }
  }

  class UILatencyLogger : CounterUsagesCollector() {
    companion object {
      private val GROUP = EventLogGroup("performance", 65)
      internal val SYSTEM_CPU_LOAD = Int("system_cpu_load")
      internal val SWAP_LOAD = Int("swap_load")
      internal val CPU_TIME = Int("cpu_time_ms")
      internal val GC_TIME = Int("gc_time_ms")
      internal val HEARTBEAT = GROUP.registerVarargEvent(
        "heartbeat",
        SYSTEM_CPU_LOAD,
        SWAP_LOAD,
        CPU_TIME,
        GC_TIME)
      @JvmField
      val LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs)
      @JvmField
      val LAGGING = GROUP.registerEvent("ui.lagging", EventFields.DurationMs)
      @JvmField
      val COLD_START = Boolean("cold_start")
      @JvmField
      val POPUP_LATENCY = GROUP.registerVarargEvent("popup.latency",
                                                    EventFields.DurationMs,
                                                    EventFields.ActionPlace,
                                                    COLD_START,
                                                    EventFields.Language)
    }

    override fun getGroup(): EventLogGroup {
      return GROUP
    }
  }
}