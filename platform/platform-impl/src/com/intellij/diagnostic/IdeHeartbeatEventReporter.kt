// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.diagnostic.opentelemetry.SafepointBean
import com.intellij.ide.PowerSaveMode
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
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
    serviceAsync<IdeHeartbeatEventReporterService>()
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
    var lastTimeToSafepoint: Long = 0
    var lastTimeAtSafepoint: Long = 0
    var lastSafepointsCount: Long = 0
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

      val timeToSafepointMs = SafepointBean.totalTimeToSafepointMs()?.let { totalTimeToSafepointMs ->
        val currentTimeToSafepoint = (totalTimeToSafepointMs - lastTimeToSafepoint).toInt()
        lastTimeToSafepoint = totalTimeToSafepointMs
        currentTimeToSafepoint
      } ?: -1
      val timeAtSafepointMs = SafepointBean.totalTimeAtSafepointMs() ?.let { totalTimeAtSafepointMs ->
        val currentTimeAtSafepoint = (totalTimeAtSafepointMs - lastTimeAtSafepoint).toInt()
        lastTimeAtSafepoint = totalTimeAtSafepointMs
        currentTimeAtSafepoint
      } ?: -1
      val safepointsCount = SafepointBean.safepointCount()?.let {  totalSafepointCount ->
        val currentSafepointsCount = (totalSafepointCount - lastSafepointsCount).toInt()
        lastSafepointsCount = totalSafepointCount
        currentSafepointsCount
      } ?: -1

      // don't report total GC time in the first 5 minutes of IJ execution
      UILatencyLogger.HEARTBEAT.log(
        UILatencyLogger.SYSTEM_CPU_LOAD.with(cpuLoadInt),
        UILatencyLogger.SWAP_LOAD.with(swapLoad),
        UILatencyLogger.CPU_TIME.with(TimeUnit.NANOSECONDS.toMillis(thisCpuTime).toInt()),

        UILatencyLogger.GC_TIME.with(thisGcTime.toInt()),

        UILatencyLogger.TIME_TO_SAFEPOINT.with(timeToSafepointMs),
        UILatencyLogger.TIME_AT_SAFEPOINT.with(timeAtSafepointMs),
        UILatencyLogger.SAFEPOINTS_COUNT.with(safepointsCount),

        UILatencyLogger.POWER_SOURCE.with(PowerStatus.getPowerStatus()),
        UILatencyLogger.POWER_SAVE_MODE.with(PowerSaveMode.isEnabled())
      )

      delay(100.seconds)
    }
  }
}

internal object UILatencyLogger : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("performance", 73)

  internal val SYSTEM_CPU_LOAD: IntEventField = Int("system_cpu_load")
  internal val SWAP_LOAD: IntEventField = Int("swap_load")
  internal val CPU_TIME: IntEventField = Int("cpu_time_ms")
  internal val GC_TIME: IntEventField = Int("gc_time_ms")

  internal val TIME_TO_SAFEPOINT: IntEventField = Int("time_to_safepoint_ms")
  internal val TIME_AT_SAFEPOINT: IntEventField = Int("time_at_safepoint_ms")
  internal val SAFEPOINTS_COUNT: IntEventField = Int("safepoints_count")

  internal val POWER_SOURCE: EnumEventField<PowerStatus> = Enum<PowerStatus>("power_source")
  internal val POWER_SAVE_MODE: BooleanEventField = Boolean("power_save_mode")
  internal val HEARTBEAT: VarargEventId = GROUP.registerVarargEvent(
    "heartbeat",
    SYSTEM_CPU_LOAD,
    SWAP_LOAD,
    CPU_TIME,

    GC_TIME,
    TIME_TO_SAFEPOINT,
    TIME_AT_SAFEPOINT,
    SAFEPOINTS_COUNT,

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

  @JvmField
  val LOW_MEMORY_CONDITION: EventId2<MemoryKind, Int> = GROUP.registerEvent("low.memory",
                                                                            Enum("type", MemoryKind::class.java),
                                                                            Int("heap_size"))

  // ==== JVMResponsivenessMonitor: overall system run-time-variability sampling

  /** number of samples in this set of measurements */
  private val SAMPLES_COUNT: IntEventField = IntEventField("samples")

  /** mean task running time, in nanoseconds */
  private val AVG_NS: FloatEventField = FloatEventField("avg_ns")

  /** 50%-tile of task running time, in nanoseconds */
  private val P50_NS: LongEventField = LongEventField("p50_ns")

  //below fields values are _relative to median_: 99%/50%, 99.9%/50%, max/50%
  private val P99_TO_P50: FloatEventField = FloatEventField("p99_to_p50")
  private val P999_TO_P50: FloatEventField = FloatEventField("p999_to_p50")
  private val MAX_TO_P50: FloatEventField = FloatEventField("max_to_p50")

  private val RESPONSIVENESS_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "responsiveness",
    AVG_NS, P50_NS,
    P99_TO_P50, P999_TO_P50, MAX_TO_P50,
    SAMPLES_COUNT
  )

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun reportResponsiveness(avg_ns: Double, p50_ns: Long, p99_ns: Long, p999_ns: Long, max_ns: Long, samplesCount: Int) {
    RESPONSIVENESS_EVENT.log(
      SAMPLES_COUNT.with(samplesCount),

      AVG_NS.with(avg_ns.toFloat()),
      P50_NS.with(p50_ns),

      P99_TO_P50.with((p99_ns * 1.0 / p50_ns).toFloat()),
      P999_TO_P50.with((p999_ns * 1.0 / p50_ns).toFloat()),
      MAX_TO_P50.with((max_ns * 1.0 / p50_ns).toFloat())
    )
  }

  @JvmStatic
  fun lowMemory(kind: MemoryKind, currentXmx: Int) {
    LOW_MEMORY_CONDITION.log(kind, currentXmx)
  }
}