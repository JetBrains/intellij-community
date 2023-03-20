// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

internal class IdeHeartbeatEventReporter : ProjectActivity {
  companion object {
    const val UI_RESPONSE_LOGGING_INTERVAL_MS = 100000
  }

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  /**
   * This is an app service because the routine should be shared between projects.
   * It's not requires on startup, so it's initialized on the first open project in [ProjectActivity].
   */
  @Service(Service.Level.APP)
  private class MyService(cs: CoroutineScope) {

    init {
      cs.launch {
        heartBeatRoutine()
      }
    }

    private suspend fun heartBeatRoutine() {
      delay(Registry.intValue("ide.heartbeat.delay").toLong())

      var lastCpuTime: Long = 0
      var lastGcTime: Long = -1
      val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
      while (true) {
        val mxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val cpuLoad: Double = mxBean.cpuLoad
        val cpuLoadInt = if (cpuLoad in 0.0..1.0) {
          (cpuLoad * 100).roundToInt()
        }
        else {
          -1
        }
        val swapSize = mxBean.totalSwapSpaceSize.toDouble()
        val swapLoad = if (swapSize > 0) ((1 - mxBean.freeSwapSpaceSize / swapSize) * 100).toInt() else 0
        val totalGcTime = gcBeans.sumOf { it.collectionTime }
        val thisGcTime = if (lastGcTime == -1L) 0 else totalGcTime - lastGcTime
        lastGcTime = thisGcTime
        val totalCpuTime = mxBean.processCpuTime
        val thisCpuTime: Long
        if (totalCpuTime < 0) {
          thisCpuTime = -1
        }
        else {
          thisCpuTime = totalCpuTime - lastCpuTime
          lastCpuTime = thisCpuTime
        }

        // don't report total GC time in the first 5 minutes of IJ execution
        UILatencyLogger.HEARTBEAT.log(
          UILatencyLogger.SYSTEM_CPU_LOAD.with(cpuLoadInt),
          UILatencyLogger.SWAP_LOAD.with(swapLoad),
          UILatencyLogger.CPU_TIME.with(TimeUnit.NANOSECONDS.toMillis(thisCpuTime).toInt()),
          UILatencyLogger.GC_TIME.with(thisGcTime.toInt())
        )

        delay(100.seconds)
      }
    }
  }

  override suspend fun execute(project: Project) {
    service<MyService>()
  }
}

internal class UILatencyLogger : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("performance", 66)
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

  override fun getGroup(): EventLogGroup = GROUP
}