// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.PowerSaveMode
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.PowerStatus
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

internal class IdeHeartbeatEventReporter : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    service<IdeHeartbeatEventReporterService>()
  }
}

/**
 * This is an app service because the routine should be shared between projects.
 * It's not required on startup, so it's initialized on the first open project in [ProjectActivity].
 */
@Service(Service.Level.APP)
private class IdeHeartbeatEventReporterService(cs: CoroutineScope) {
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
        UILatencyLogger.GC_TIME.with(thisGcTime.toInt()),
        UILatencyLogger.POWER_SOURCE.with(PowerStatus.getPowerStatus()),
        UILatencyLogger.POWER_SAVE_MODE.with(PowerSaveMode.isEnabled())
      )

      delay(100.seconds)
    }
  }
}

internal object UILatencyLogger : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("performance", 69)

  internal val SYSTEM_CPU_LOAD: IntEventField = Int("system_cpu_load")
  internal val SWAP_LOAD: IntEventField = Int("swap_load")
  internal val CPU_TIME: IntEventField = Int("cpu_time_ms")
  internal val GC_TIME: IntEventField = Int("gc_time_ms")
  internal val POWER_SOURCE: EnumEventField<PowerStatus> = Enum<PowerStatus>("power_source")
  internal val POWER_SAVE_MODE: BooleanEventField = Boolean("power_save_mode")
  internal val HEARTBEAT: VarargEventId = GROUP.registerVarargEvent(
    "heartbeat",
    SYSTEM_CPU_LOAD,
    SWAP_LOAD,
    CPU_TIME,
    GC_TIME,
    POWER_SOURCE,
    POWER_SAVE_MODE
  )

  @JvmField
  val LATENCY: EventId1<Long> = GROUP.registerEvent("ui.latency", EventFields.DurationMs)

  @JvmField
  val LAGGING: EventId2<Long, Boolean> = GROUP.registerEvent("ui.lagging", EventFields.DurationMs, Boolean("during_indexing"))

  @JvmField
  val COLD_START: BooleanEventField = Boolean("cold_start")

  @JvmField
  val ACTION_POPUP_LATENCY: VarargEventId = GROUP.registerVarargEvent("popup.latency",
                                                                      EventFields.DurationMs,
                                                                      EventFields.ActionPlace,
                                                                      COLD_START,
                                                                      EventFields.Language)

  @Suppress("SpellCheckingInspection")
  @JvmField
  val MAIN_MENU_LATENCY: EventId1<Long> = GROUP.registerEvent("mainmenu.latency", EventFields.DurationMs)

  override fun getGroup(): EventLogGroup = GROUP
}