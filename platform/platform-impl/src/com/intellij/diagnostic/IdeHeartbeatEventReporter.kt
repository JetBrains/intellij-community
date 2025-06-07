// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.diagnostic.opentelemetry.SafepointBean
import com.intellij.ide.PowerSaveMode
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByInlineRegexp
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
import kotlin.time.Duration
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

internal data class EventDurationHistogram(
  val totalCount: Int,
  /**
   * sum of measurements for all events
   */
  val totalDuration: Duration,
  /**
   * Median duration of an event
   */
  val p50: Duration,
  // higher percentiles of duration
  val p95: Duration,
  val p99: Duration,
)

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
      val timeAtSafepointMs = SafepointBean.totalTimeAtSafepointMs()?.let { totalTimeAtSafepointMs ->
        val currentTimeAtSafepoint = (totalTimeAtSafepointMs - lastTimeAtSafepoint).toInt()
        lastTimeAtSafepoint = totalTimeAtSafepointMs
        currentTimeAtSafepoint
      } ?: -1
      val safepointsCount = SafepointBean.safepointCount()?.let { totalSafepointCount ->
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
  private val GROUP = EventLogGroup("performance", 79)

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

  private val MEMORY_TYPE_FIELD = Enum("type", MemoryKind::class.java)
  private val HEAP_SIZE_FIELD = Int("heap_size_gigabytes")
  private val PROJECT_COUNT_FIELD = Int("project_count")
  private val IS_OOM_HAPPENED_FIELD = Boolean("oom_error")
  private val IS_FROM_CRASH_FIELD = Boolean("oom_crash")
  private val LAST_ACTION_FIELD = ActionsEventLogGroup.ActionIdField("last_action_id")

  @JvmField
  val LOW_MEMORY_CONDITION: VarargEventId = GROUP.registerVarargEvent("low.memory",
                                                                      MEMORY_TYPE_FIELD,
                                                                      HEAP_SIZE_FIELD,
                                                                      PROJECT_COUNT_FIELD,
                                                                      IS_OOM_HAPPENED_FIELD,
                                                                      IS_FROM_CRASH_FIELD,
                                                                      LAST_ACTION_FIELD,
                                                                      EventFields.Dumb)

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

  // ==== Detailed UI Thread statistics: information about latency and throughput of UI Event Queue

  val UI_EVENTS_COUNT: IntEventField = Int("ui_execution_events_count", description = "Total number of UI events executed by the AWT EventQueue.")

  val WINDOW_LENGTH_MS: IntEventField = Int("window_length_ms", description = "The duration of measurement window in milliseconds.")

  val UI_EXECUTION_TIME_TOTAL_MS: IntEventField = Int("ui_execution_total_ms", description = "Total time spent on executing UI events in milliseconds.")
  val UI_EXECUTION_TIME_50_US: IntEventField = Int("ui_execution_p50_us", description = "Median duration of execution of a UI event in microseconds.")
  val UI_EXECUTION_TIME_95_TO_50: FloatEventField = FloatEventField("ui_execution_p95_to_p50", description = "Relation of 95-th percentile of a UI event execution to the median")
  val UI_EXECUTION_TIME_99_TO_50: FloatEventField = FloatEventField("ui_execution_p99_to_p50", description = "Relation of 99-th percentile of a UI event execution to the median")

  val INVOCATION_EVENTS_COUNT: IntEventField = Int("invocation_events_count", description = "Number of executed invocation events. Events skipped because of modality mismatch are not counted.")

  val INVOCATION_WAITING_TIME_TOTAL_MS: IntEventField = Int("invocation_waiting_total_ms", description = "Sum over times of each invocation event spending in the event queue in milliseconds.")
  val INVOCATION_WAITING_TIME_50_US: IntEventField = Int("invocation_waiting_p50_us", description = "Median waiting time of an invocation event in microseconds.")
  val INVOCATION_WAITING_TIME_95_TO_50: FloatEventField = FloatEventField("invocation_waiting_p95_to_p50", description = "Relation of 95-th percentile of an invocation event waiting to the median")
  val INVOCATION_WAITING_TIME_99_TO_50: FloatEventField = FloatEventField("invocation_waiting_p99_to_p50", description = "Relation of 99-th percentile of an invocation event waiting to the median")

  val INVOCATION_EXECUTION_TIME_TOTAL_MS: IntEventField = Int("invocation_execution_total_ms", description = "Total time spent on executing invocation events in milliseconds.")
  val INVOCATION_EXECUTION_TIME_50_US: IntEventField = Int("invocation_execution_p50_us", description = "Median execution time of an invocation events in microseconds.")
  val INVOCATION_EXECUTION_TIME_95_TO_50: FloatEventField = FloatEventField("invocation_execution_p95_to_p50", description = "Relation of 95-th percentile of an invocation event execution to the median")
  val INVOCATION_EXECUTION_TIME_99_TO_50: FloatEventField = FloatEventField("invocation_execution_p99_to_p50", description = "Relation of 99-th percentile of an invocation event execution to the median")

  val WRITE_LOCK_EVENTS: IntEventField = Int("write_lock_events_count", description = "Number of requests for write lock")

  val WRITE_LOCK_WAITING_TIME_TOTAL_MS: IntEventField = Int("write_lock_waiting_ms", description = "Total time spent on waiting for acquisition of the write lock in milliseconds.")
  val WRITE_LOCK_WAITING_TIME_50_US: IntEventField = Int("write_lock_waiting_p50_us", description = "Median waiting time for the write lock in microseconds.")
  val WRITE_LOCK_WAITING_TIME_95_TO_50: FloatEventField = FloatEventField("write_lock_waiting_p95_to_p50", description = "Relation of 95-th percentile of a write lock acquisition to the median")
  val WRITE_LOCK_WAITING_TIME_99_TO_50: FloatEventField = FloatEventField("write_lock_waiting_p99_to_p50", description = "Relation of 99-th percentile of a write lock acquisition to the median")

  val WRITE_LOCK_EXECUTION_TIME_TOTAL_MS: IntEventField = Int("write_lock_execution_ms", description = "Total time spent on execution of write actions in milliseconds.")
  val WRITE_LOCK_EXECUTION_TIME_50_US: IntEventField = Int("write_lock_execution_p50_us", description = "Median execution time of write actions in microseconds.")
  val WRITE_LOCK_EXECUTION_TIME_95_TO_50: FloatEventField = FloatEventField("write_lock_execution_p95_to_p50", description = "Relation of 95-th percentile of a write action execution time to the median")
  val WRITE_LOCK_EXECUTION_TIME_99_TO_50: FloatEventField = FloatEventField("write_lock_execution_p99_to_p50", description = "Relation of 99-th percentile of a write action execution time to the median")

  val READING_LOCK_EVENTS: IntEventField = Int("reading_lock_events_count", description = "Number of events for read and write-intent locks")

  val READING_LOCK_WAITING_TIME_TOTAL_MS: IntEventField = Int("reading_lock_waiting_ms", description = "Total time spent on waiting for read and write-intent locks in milliseconds.")
  val READING_LOCK_WAITING_TIME_50_US: IntEventField = Int("reading_lock_waiting_p50_us", description = "Median waiting time for the read and write-intent locks in microseconds.")
  val READING_LOCK_WAITING_TIME_95_TO_50: FloatEventField = FloatEventField("reading_lock_waiting_p95_to_p50", description = "Relation of 95-th percentile of read and write-intent locks waiting to the median")
  val READING_LOCK_WAITING_TIME_99_TO_50: FloatEventField = FloatEventField("reading_lock_waiting_p99_to_p50", description = "Relation of 99-th percentile of read and write-intent locks waiting to the median")

  val READING_LOCK_EXECUTION_TIME_TOTAL_MS: IntEventField = Int("reading_lock_execution_ms", description = "Total time spent on execution of read and write-intent actions in milliseconds.")
  val READING_LOCK_EXECUTION_TIME_50_US: IntEventField = Int("reading_lock_execution_p50_us", description = "Median execution time of read and write-intent actions in microseconds.")
  val READING_LOCK_EXECUTION_TIME_95_TO_50: FloatEventField = FloatEventField("reading_lock_execution_p95_to_p50", description = "Relation of 95-th percentile of read and write-intent actions execution time to the median")
  val READING_LOCK_EXECUTION_TIME_99_TO_50: FloatEventField = FloatEventField("reading_lock_execution_p99_to_p50", description = "Relation of 99-th percentile of read and write-intent actions execution time to the median")

  private val UI_RESPONSIVENESS: VarargEventId = GROUP.registerVarargEvent(
    eventId = "ui.responsiveness",
    description = "Aggregated distribution of latencies for execution and queueing of UI events. We also gather information about the Read/Write lock acquisitions on UI thread in this event.",
    UI_EVENTS_COUNT,
    WINDOW_LENGTH_MS,
    UI_EXECUTION_TIME_TOTAL_MS,
    UI_EXECUTION_TIME_50_US,
    UI_EXECUTION_TIME_95_TO_50,
    UI_EXECUTION_TIME_99_TO_50,
    INVOCATION_EVENTS_COUNT,
    INVOCATION_WAITING_TIME_TOTAL_MS,
    INVOCATION_WAITING_TIME_50_US,
    INVOCATION_WAITING_TIME_95_TO_50,
    INVOCATION_WAITING_TIME_99_TO_50,
    INVOCATION_EXECUTION_TIME_TOTAL_MS,
    INVOCATION_EXECUTION_TIME_50_US,
    INVOCATION_EXECUTION_TIME_95_TO_50,
    INVOCATION_EXECUTION_TIME_99_TO_50,
    WRITE_LOCK_EVENTS,
    WRITE_LOCK_WAITING_TIME_TOTAL_MS,
    WRITE_LOCK_WAITING_TIME_50_US,
    WRITE_LOCK_WAITING_TIME_95_TO_50,
    WRITE_LOCK_WAITING_TIME_99_TO_50,
    WRITE_LOCK_EXECUTION_TIME_TOTAL_MS,
    WRITE_LOCK_EXECUTION_TIME_50_US,
    WRITE_LOCK_EXECUTION_TIME_95_TO_50,
    WRITE_LOCK_EXECUTION_TIME_99_TO_50,
    READING_LOCK_EVENTS,
    READING_LOCK_WAITING_TIME_TOTAL_MS,
    READING_LOCK_WAITING_TIME_50_US,
    READING_LOCK_WAITING_TIME_95_TO_50,
    READING_LOCK_WAITING_TIME_99_TO_50,
    READING_LOCK_EXECUTION_TIME_TOTAL_MS,
    READING_LOCK_EXECUTION_TIME_50_US,
    READING_LOCK_EXECUTION_TIME_95_TO_50,
    READING_LOCK_EXECUTION_TIME_99_TO_50,
  )

  fun reportUiResponsiveness(
    windowLength: Duration,
    totalExecution: EventDurationHistogram,
    invocationEventsWaiting: EventDurationHistogram,
    invocationEventsExecution: EventDurationHistogram,
    writeLockWaiting: EventDurationHistogram,
    writeLockExecution: EventDurationHistogram,
    readingLockWaiting: EventDurationHistogram,
    readingLockExecution: EventDurationHistogram,
  ) {
    UI_RESPONSIVENESS.log(
      UI_EVENTS_COUNT.with(totalExecution.totalCount),
      WINDOW_LENGTH_MS.with(windowLength.inWholeMilliseconds.toInt()),
      *reportDurationHistograms(totalExecution, UI_EXECUTION_TIME_TOTAL_MS, UI_EXECUTION_TIME_50_US, UI_EXECUTION_TIME_95_TO_50, UI_EXECUTION_TIME_99_TO_50),
      INVOCATION_EVENTS_COUNT.with(invocationEventsWaiting.totalCount),
      *reportDurationHistograms(invocationEventsWaiting, INVOCATION_WAITING_TIME_TOTAL_MS, INVOCATION_WAITING_TIME_50_US, INVOCATION_WAITING_TIME_95_TO_50, INVOCATION_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(invocationEventsExecution, INVOCATION_EXECUTION_TIME_TOTAL_MS, INVOCATION_EXECUTION_TIME_50_US, INVOCATION_EXECUTION_TIME_95_TO_50, INVOCATION_EXECUTION_TIME_99_TO_50),
      // some write lock requests may be upgraded from write-intent lock
      // we don't think it is useful to know the exact number of events that are blocked on write lock acquisition
      WRITE_LOCK_EVENTS.with(writeLockExecution.totalCount),
      *reportDurationHistograms(writeLockWaiting, WRITE_LOCK_WAITING_TIME_TOTAL_MS, WRITE_LOCK_WAITING_TIME_50_US, WRITE_LOCK_WAITING_TIME_95_TO_50, WRITE_LOCK_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(writeLockExecution, WRITE_LOCK_EXECUTION_TIME_TOTAL_MS, WRITE_LOCK_EXECUTION_TIME_50_US, WRITE_LOCK_EXECUTION_TIME_95_TO_50, WRITE_LOCK_EXECUTION_TIME_99_TO_50),
      READING_LOCK_EVENTS.with(readingLockExecution.totalCount),
      *reportDurationHistograms(readingLockWaiting, READING_LOCK_WAITING_TIME_TOTAL_MS, READING_LOCK_WAITING_TIME_50_US, READING_LOCK_WAITING_TIME_95_TO_50, READING_LOCK_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(readingLockExecution, READING_LOCK_EXECUTION_TIME_TOTAL_MS, READING_LOCK_EXECUTION_TIME_50_US, READING_LOCK_EXECUTION_TIME_95_TO_50, READING_LOCK_EXECUTION_TIME_99_TO_50),
    )
  }

  private fun reportDurationHistograms(
    histogram: EventDurationHistogram,
    totalDuration: IntEventField,
    p50: IntEventField,
    p95_to_50: FloatEventField,
    p99_to_50: FloatEventField,
  ): Array<out EventPair<out Any>> {
    val median = histogram.p50.inWholeMicroseconds.toInt()
    val p95 = if (median == 0) 0f else histogram.p95.inWholeMicroseconds.toFloat() / median.toFloat()
    val p99 = if (median == 0) 0f else histogram.p95.inWholeMicroseconds.toFloat() / median.toFloat()
    return arrayOf(
      totalDuration.with(histogram.totalDuration.inWholeMilliseconds.toInt()),
      p50.with(median),
      p95_to_50.with(p95),
      p99_to_50.with(p99),
    )
  }

  private val MEM_HISTOGRAM_BUCKETS = longArrayOf(
    1*1024, // 1g
    5*256, 6*256, 7*256, 8*256, // 2g
    9*256, 10*256, 11*256, 12*256, // 3g
    7*512, 8*512, // 4g
    9*512, 10*512, // 5g
    6*1024,
    7*1024,
    8*1024,
    9*1024,
    10*1024,
    11*1024,
    12*1024,
    13*1024,
    14*1024,
    15*1024,
    16*1024,
  )
  private val MEM_XMX_FIELD = EventFields.BoundedInt("xmx", intArrayOf(512, 768, 1024, 1536, 2048, 4096, 6000, 8192, 12288, 16384))
  private val MEM_SAMPLES_FIELD = IntEventField("samples")
  private val MEM_HISTOGRAM_TOTAL1_FIELD = EventFields.IntList(
    "ram_minus_file_mappings",
    description = "OS-provided process memory usage `RAM - FileMappings`; sampled every second, aggregated into a histogram; " +
                  "buckets=${MEM_HISTOGRAM_BUCKETS.contentToString()}"
  )
  private val MEM_HISTOGRAM_TOTAL2_FIELD = EventFields.IntList(
    "ram_plus_swap_minus_file_mappings",
    description = "OS-provided process memory usage `RAM + SWAP - FileMappings`; sampled every second, aggregated into a histogram; " +
                  "buckets=${MEM_HISTOGRAM_BUCKETS.contentToString()}"
  )
  private val MEM_HEARTBEAT_EVENT = GROUP.registerVarargEvent(
    "heartbeat.memory",
    description = "Reported every hour; sampled every second",
    MEM_XMX_FIELD,
    MEM_SAMPLES_FIELD,
    MEM_HISTOGRAM_TOTAL1_FIELD,
    MEM_HISTOGRAM_TOTAL2_FIELD,
  )

  @JvmField
  internal val SLOW_OPERATIONS_ISSUES = GROUP.registerEvent("slow.operation.issues",
                                                            StringListValidatedByInlineRegexp("issue_id", "[A-Z]{2,7}-\\d{1,6}"))

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
  fun lowMemory(
    kind: MemoryKind,
    currentXmxMegabytes: Int,
    projectCount: Int,
    oomError: Boolean,
    fromCrashReport: Boolean,
    dumbMode: Boolean,
  ) {
    LOW_MEMORY_CONDITION.log(
      MEMORY_TYPE_FIELD.with(kind),
      HEAP_SIZE_FIELD.with((currentXmxMegabytes.toDouble() / 1024).roundToInt()),
      PROJECT_COUNT_FIELD.with(projectCount),
      IS_OOM_HAPPENED_FIELD.with(oomError),
      IS_FROM_CRASH_FIELD.with(fromCrashReport),
      LAST_ACTION_FIELD.with(IdeaLogger.ourLastActionId),
      EventFields.Dumb.with(dumbMode)
    )
  }

  class MemoryStatsSampler {
    private val provider = PlatformMemoryUtil.getInstance().newMemoryStatsProvider()
    private var samples: Int = 0
    private var total1 = newHistogram()
    private var total2 = newHistogram()

    fun sample() {
      val stats = provider.getCurrentProcessMemoryStats() ?: return
      total1.addValue(stats.ramMinusFileMappings / 1024 / 1024)
      total2.addValue(stats.ramPlusSwapMinusFileMappings / 1024 / 1024)
      samples++
    }

    fun logToFus() {
      val xmxMb = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max / 1024 / 1024
      MEM_HEARTBEAT_EVENT.log(
        MEM_XMX_FIELD.with(xmxMb.toInt()),
        MEM_SAMPLES_FIELD.with(samples),
        MEM_HISTOGRAM_TOTAL1_FIELD.with(total1.build().buckets.toList()),
        MEM_HISTOGRAM_TOTAL2_FIELD.with(total2.build().buckets.toList()),
      )

      samples = 0
      total1 = newHistogram()
      total2 = newHistogram()
    }

    fun close() {
      provider.close()
    }

    private fun newHistogram(): FusHistogramBuilder {
      return FusHistogramBuilder(MEM_HISTOGRAM_BUCKETS, FusHistogramBuilder.RoundingDirection.UP)
    }
  }
}