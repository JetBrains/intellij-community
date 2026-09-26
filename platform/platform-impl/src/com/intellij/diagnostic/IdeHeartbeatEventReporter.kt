// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.diagnostic.opentelemetry.SafepointBean
import com.intellij.ide.PowerSaveMode
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.FloatEventField
import com.intellij.internal.statistic.eventLog.events.FusHistogramBuilder
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.io.PowerStatus
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal class IdeHeartbeatEventReporter : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    RegistryManager.getInstanceAsync() // await Registry before loading service

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

/** The OS memory counter that backs the reported process footprint. */
internal enum class FootprintSource { RSS, PRIVATE_BYTES, PHYS_FOOTPRINT }

/**
 * This is an app service because the routine should be shared between projects.
 * It's not required on startup, so it's initialized on the first open project in [ProjectActivity].
 */
@Service(Service.Level.APP)
private class IdeHeartbeatEventReporterService(cs: CoroutineScope) {
  init {
    cs.launch(Dispatchers.IO) {
      heartBeatRoutine()
    }
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  private suspend fun heartBeatRoutine() {
    delay(RegistryManager.getInstanceAsync().intValue("ide.heartbeat.delay").toLong().milliseconds)

    val cpuTimeDiffer = LongDiffer(0)//cpu time is in nanoseconds
    //other durations are in milliseconds by default:
    val gcDurationDiffer = LongDiffer(0)
    val timeToSafepointDiffer = LongDiffer(0)
    val timeAtSafepointDiffer = LongDiffer(0)
    val safepointsCountDiffer = LongDiffer(0)

    val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    val mxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    while (true) {
      val cpuLoad: Double = mxBean.cpuLoad
      val cpuLoadInPercents = if (cpuLoad in 0.0..1.0) {
        (cpuLoad * 100).roundToInt()
      }
      else {
        -1
      }
      val swapSize = mxBean.totalSwapSpaceSize.toDouble()
      val swapLoadInPercents = if (swapSize > 0) ((1 - mxBean.freeSwapSpaceSize / swapSize) * 100).toInt() else 0

      val accumulatedGcDuration = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
      val gcDurationInPeriodMs = gcDurationDiffer.toDiff(accumulatedGcDuration)

      val accumulatedCpuTimeNs = mxBean.processCpuTime
      val cpuTimeInPeriodMs = if (accumulatedCpuTimeNs < 0) {
        -1 //marker 'metric is not available'
      }
      else {
        NANOSECONDS.toMillis(cpuTimeDiffer.toDiff(accumulatedCpuTimeNs)).toInt()
      }

      val timeToSafepointMs = SafepointBean.totalTimeToSafepointMs()?.let { totalTimeToSafepointMs ->
        timeToSafepointDiffer.toDiff(totalTimeToSafepointMs).toInt()
      } ?: -1
      val timeAtSafepointMs = SafepointBean.totalTimeAtSafepointMs()?.let { totalTimeAtSafepointMs ->
        timeAtSafepointDiffer.toDiff(totalTimeAtSafepointMs).toInt()
      } ?: -1
      val safepointsCount = SafepointBean.safepointCount()?.let { totalSafepointCount ->
        safepointsCountDiffer.toDiff(totalSafepointCount).toInt()
      } ?: -1

      // don't report total GC time in the first 5 minutes of IJ execution
      UILatencyLogger.reportHeartbeat(
        cpuLoadInPercents, swapLoadInPercents, cpuTimeInPeriodMs, gcDurationInPeriodMs,
        timeToSafepointMs, timeAtSafepointMs, safepointsCount
      )

      // The footprint is not available when the OS does not report the process memory stats.
      var footprintMb: Int? = null
      var footprintSource: FootprintSource? = null
      val memoryStats = PlatformMemoryUtil.getInstance().getCurrentProcessMemoryStats()
      if (memoryStats != null) {
        val source = when (OS.CURRENT) {
          OS.Linux -> FootprintSource.RSS
          OS.macOS -> FootprintSource.PHYS_FOOTPRINT
          else -> FootprintSource.PRIVATE_BYTES
        }
        val footprintBytes = when (source) {
          FootprintSource.RSS -> memoryStats.ram
          FootprintSource.PRIVATE_BYTES, FootprintSource.PHYS_FOOTPRINT -> memoryStats.ramPlusSwapMinusFileMappings
        }
        footprintSource = source
        footprintMb = toMegabytes(footprintBytes)
      }

      val heapCommittedMb = toMegabytes(ManagementFactory.getMemoryMXBean().heapMemoryUsage.committed)

      var oldGenLiveSetBytes = -1L
      var metaspaceCommittedBytes = -1L
      var codeCacheCommittedBytes = -1L
      for (pool in ManagementFactory.getMemoryPoolMXBeans()) {
        val name = pool.name
        when {
          pool.type == MemoryType.HEAP && (name.contains("Old Gen") || name.contains("Tenured Gen")) -> {
            // `getCollectionUsage` is the pool usage right after the last GC of the pool, so it is the live set after an old GC.
            pool.collectionUsage?.let {
              if (oldGenLiveSetBytes < 0) oldGenLiveSetBytes = 0
              oldGenLiveSetBytes += it.used
            }
          }
          name == "Metaspace" -> pool.usage?.let { metaspaceCommittedBytes = it.committed }
          name == "Code Cache" || name.startsWith("CodeHeap") -> pool.usage?.let {
            if (codeCacheCommittedBytes < 0) codeCacheCommittedBytes = 0
            codeCacheCommittedBytes += it.committed
          }
        }
      }

      var oldGcTimeMs = -1L
      var oldGcCount = -1L
      for (gc in gcBeans) {
        if (isOldGenGcBean(gc.name)) {
          oldGcTimeMs = gc.collectionTime.coerceAtLeast(0)
          oldGcCount = gc.collectionCount.coerceAtLeast(0)
          break
        }
      }

      UILatencyLogger.reportMemorySampled(
        footprintMb = footprintMb,
        footprintSource = footprintSource,
        heapLiveSetMb = toMegabytesOrUnavailable(oldGenLiveSetBytes),
        heapCommittedMb = heapCommittedMb,
        metaspaceCommittedMb = toMegabytesOrUnavailable(metaspaceCommittedBytes),
        codeCacheCommittedMb = toMegabytesOrUnavailable(codeCacheCommittedBytes),
        oldGcTimeMs = oldGcTimeMs,
        oldGcCount = oldGcCount,
      )

      delay(60.seconds)
    }
  }

  private fun isOldGenGcBean(name: String): Boolean =
    name == "MarkSweepCompact" ||    // -XX:+UseSerialGC
    name == "PS MarkSweep" ||        // -XX:+UseParallelGC
    name == "ConcurrentMarkSweep" || // -XX:+UseConcMarkSweepGC
    name == "G1 Old Generation"      // -XX:+UseG1GC

  private fun toMegabytes(bytes: Long): Int = (bytes / 1024 / 1024).toInt()

  /** @return -1 if [bytes] is negative, which is the marker 'metric is not available' */
  private fun toMegabytesOrUnavailable(bytes: Long): Int = if (bytes < 0) -1 else toMegabytes(bytes)
}

internal object UILatencyLogger : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("performance", 83)

  private val SYSTEM_CPU_LOAD = EventFields.Int("system_cpu_load")
  private val SWAP_LOAD = EventFields.Int("swap_load")
  private val CPU_TIME = EventFields.Int("cpu_time_ms")
  private val GC_TIME = EventFields.Int("gc_time_ms")

  private val TIME_TO_SAFEPOINT = EventFields.Int("time_to_safepoint_ms")
  private val TIME_AT_SAFEPOINT = EventFields.Int("time_at_safepoint_ms")
  private val SAFEPOINTS_COUNT = EventFields.Int("safepoints_count")

  private val POWER_SOURCE = EventFields.Enum<PowerStatus>("power_source")
  private val POWER_SAVE_MODE = EventFields.Boolean("power_save_mode")
  private val HEARTBEAT = GROUP.registerVarargEvent(
    "heartbeat",
    SYSTEM_CPU_LOAD, SWAP_LOAD, CPU_TIME, GC_TIME, TIME_TO_SAFEPOINT, TIME_AT_SAFEPOINT, SAFEPOINTS_COUNT,
    POWER_SOURCE, POWER_SAVE_MODE
  )

  fun reportHeartbeat(
    cpuLoadInPercents: Int,
    swapLoadInPercents: Int,
    cpuTimeInPeriodMs: Int,
    gcDurationInPeriodMs: Long,
    timeToSafepointMs: Int,
    timeAtSafepointMs: Int,
    safepointsCount: Int,
  ) {
    HEARTBEAT.log(
      SYSTEM_CPU_LOAD.with(cpuLoadInPercents),
      SWAP_LOAD.with(swapLoadInPercents),
      CPU_TIME.with(cpuTimeInPeriodMs),

      GC_TIME.with(gcDurationInPeriodMs.toInt()),

      TIME_TO_SAFEPOINT.with(timeToSafepointMs),
      TIME_AT_SAFEPOINT.with(timeAtSafepointMs),
      SAFEPOINTS_COUNT.with(safepointsCount),

      POWER_SOURCE.with(PowerStatus.getPowerStatus()),
      POWER_SAVE_MODE.with(PowerSaveMode.isEnabled())
    )
  }

  private val FOOTPRINT_MB_LAST = EventFields.Int(
    "footprint_mb_last",
    "Instantaneous process memory footprint in megabytes. It is RSS on Linux, Private Bytes on Windows, and phys_footprint on macOS."
  )
  private val FOOTPRINT_SOURCE = EventFields.Enum<FootprintSource>(
    "footprint_source",
    "The OS memory counter that backs the footprint value."
  )
  private val HEAP_LIVE_SET_MB = EventFields.Int(
    "heap_live_set_mb",
    "Live heap set in megabytes, measured after the last old-generation GC. It is -1 when the value is not available."
  )
  private val HEAP_COMMITTED_MB_LAST = EventFields.Int(
    "heap_committed_mb_last",
    "Committed heap size in megabytes. It includes all allocated heap, not only the live set."
  )
  private val METASPACE_COMMITTED_MB_LAST = EventFields.Int(
    "metaspace_committed_mb_last",
    "Committed metaspace size in megabytes. It is -1 when the value is not available."
  )
  private val CODECACHE_COMMITTED_MB_LAST = EventFields.Int(
    "codecache_committed_mb_last",
    "Committed code cache size in megabytes. It is -1 when the value is not available."
  )
  private val GC_OLD_TIME_MS_TOTAL = EventFields.Long(
    "gc_old_time_ms_total",
    "Total time in milliseconds in old-generation GC since JVM start. It is -1 when the value is not available."
  )
  private val GC_OLD_COUNT_TOTAL = EventFields.Long(
    "gc_old_count_total",
    "Total number of old-generation GC collections since JVM start. It is -1 when the value is not available."
  )
  private val MEMORY_SAMPLED_EVENT = GROUP.registerVarargEvent(
    "memory.sampled",
    FOOTPRINT_MB_LAST, FOOTPRINT_SOURCE, HEAP_LIVE_SET_MB, HEAP_COMMITTED_MB_LAST,
    METASPACE_COMMITTED_MB_LAST, CODECACHE_COMMITTED_MB_LAST, GC_OLD_TIME_MS_TOTAL, GC_OLD_COUNT_TOTAL
  )

  /**
   * [footprintMb] and [footprintSource] are null together when the OS does not report the process memory stats.
   * Then the event holds no footprint field.
   */
  internal fun reportMemorySampled(
    footprintMb: Int?,
    footprintSource: FootprintSource?,
    heapLiveSetMb: Int,
    heapCommittedMb: Int,
    metaspaceCommittedMb: Int,
    codeCacheCommittedMb: Int,
    oldGcTimeMs: Long,
    oldGcCount: Long,
  ) {
    val pairs = mutableListOf<EventPair<*>>()

    if (footprintMb != null && footprintSource != null) {
      pairs += FOOTPRINT_MB_LAST.with(footprintMb)
      pairs += FOOTPRINT_SOURCE.with(footprintSource)
    }

    pairs += HEAP_COMMITTED_MB_LAST.with(heapCommittedMb)
    pairs += HEAP_LIVE_SET_MB.with(heapLiveSetMb)
    pairs += METASPACE_COMMITTED_MB_LAST.with(metaspaceCommittedMb)
    pairs += CODECACHE_COMMITTED_MB_LAST.with(codeCacheCommittedMb)
    pairs += GC_OLD_TIME_MS_TOTAL.with(oldGcTimeMs)
    pairs += GC_OLD_COUNT_TOTAL.with(oldGcCount)

    MEMORY_SAMPLED_EVENT.log(pairs)
  }

  private val LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs)
  private val LAGGING = GROUP.registerEvent(
    eventId = "ui.lagging",
    eventField1 = EventFields.DurationMs,
    eventField2 = EventFields.Boolean("during_indexing"),
    eventField3 = EventFields.Boolean("freeze_popup_shown",
                                      description = "Whether the Freeze Popup was shown (or going to be shown) during the UI lag. " +
                                                    "The appearance of the popup indicates that the UI thread was processing the Read-Write lock."))
  private val COLD_START = EventFields.Boolean("cold_start")
  private val ACTION_POPUP_LATENCY = GROUP.registerVarargEvent(
    "popup.latency",
    EventFields.DurationMs, EventFields.ActionPlace, COLD_START, EventFields.Language
  )
  private val MAIN_MENU_LATENCY = GROUP.registerEvent("mainmenu.latency", EventFields.DurationMs)

  fun logLatency(latencyMs: Long) {
    LATENCY.log(latencyMs)
  }

  fun logLagging(latencyMs: Long, hasIndexingGoingOn: Boolean, wasFreezePopupShown: Boolean) {
    LAGGING.log(latencyMs, hasIndexingGoingOn, wasFreezePopupShown)
  }

  @JvmStatic
  fun logActionPopupLatency(time: Long, place: String, coldStart: Boolean, language: Language?) {
    ACTION_POPUP_LATENCY.log(
      EventFields.DurationMs.with(time),
      EventFields.ActionPlace.with(place),
      COLD_START.with(coldStart),
      EventFields.Language.with(language)
    )
  }

  fun logMainMenuLatency(startNs: Long) {
    MAIN_MENU_LATENCY.log((System.nanoTime() - startNs).nanoseconds.inWholeMilliseconds)
  }

  private val MEMORY_TYPE_FIELD = EventFields.Enum<MemoryKind>("type")
  private val HEAP_SIZE_FIELD = EventFields.Int("heap_size_gigabytes")
  private val PROJECT_COUNT_FIELD = EventFields.Int("project_count")
  private val IS_OOM_HAPPENED_FIELD = EventFields.Boolean("oom_error")
  private val IS_FROM_CRASH_FIELD = EventFields.Boolean("oom_crash")
  private val LAST_ACTION_FIELD = ActionsEventLogGroup.ActionIdField("last_action_id")
  private val LOW_MEMORY_CONDITION = GROUP.registerVarargEvent(
    "low.memory",
    MEMORY_TYPE_FIELD, HEAP_SIZE_FIELD, PROJECT_COUNT_FIELD, IS_OOM_HAPPENED_FIELD, IS_FROM_CRASH_FIELD, LAST_ACTION_FIELD, EventFields.Dumb
  )

  // ==== JVMResponsivenessMonitor: overall system run-time-variability sampling

  /** number of samples in this set of measurements */
  private val SAMPLES_COUNT = EventFields.Int("samples")

  /** mean task running time, in nanoseconds */
  private val AVG_NS = EventFields.Float("avg_ns")

  /** 50%-tile of task running time, in nanoseconds */
  private val P50_NS = EventFields.Long("p50_ns")

  //below fields values are _relative to median_: 99%/50%, 99.9%/50%, max/50%
  private val P99_TO_P50 = EventFields.Float("p99_to_p50")
  private val P999_TO_P50 = EventFields.Float("p999_to_p50")
  private val MAX_TO_P50 = EventFields.Float("max_to_p50")

  private val RESPONSIVENESS_EVENT = GROUP.registerVarargEvent(
    "responsiveness",
    AVG_NS, P50_NS,
    P99_TO_P50, P999_TO_P50, MAX_TO_P50,
    SAMPLES_COUNT
  )

  // ==== Detailed UI Thread statistics: information about latency and throughput of UI Event Queue

  private val UI_EVENTS_COUNT = EventFields.Int("ui_execution_events_count", "Total number of UI events executed by the AWT EventQueue.")

  private val WINDOW_LENGTH_MS = EventFields.Int("window_length_ms", "The duration of measurement window in milliseconds.")

  private val UI_EXECUTION_TIME_TOTAL_MS =
    EventFields.Int("ui_execution_total_ms", "Total time spent on executing UI events in milliseconds.")
  private val UI_EXECUTION_TIME_50_US =
    EventFields.Int("ui_execution_p50_us", "Median duration of execution of a UI event in microseconds.")
  private val UI_EXECUTION_TIME_95_TO_50 =
    EventFields.Float("ui_execution_p95_to_p50", "Relation of 95-th percentile of a UI event execution to the median")
  private val UI_EXECUTION_TIME_99_TO_50 =
    EventFields.Float("ui_execution_p99_to_p50", "Relation of 99-th percentile of a UI event execution to the median")

  private val INVOCATION_EVENTS_COUNT = EventFields.Int("invocation_events_count",
                                                        "Number of executed invocation events. Events skipped because of modality mismatch are not counted.")

  private val INVOCATION_WAITING_TIME_TOTAL_MS =
    EventFields.Int("invocation_waiting_total_ms", "Sum over times of each invocation event spending in the event queue in milliseconds.")
  private val INVOCATION_WAITING_TIME_50_US =
    EventFields.Int("invocation_waiting_p50_us", "Median waiting time of an invocation event in microseconds.")
  private val INVOCATION_WAITING_TIME_95_TO_50 =
    EventFields.Float("invocation_waiting_p95_to_p50", "Relation of 95-th percentile of an invocation event waiting to the median")
  private val INVOCATION_WAITING_TIME_99_TO_50 =
    EventFields.Float("invocation_waiting_p99_to_p50", "Relation of 99-th percentile of an invocation event waiting to the median")

  private val INVOCATION_EXECUTION_TIME_TOTAL_MS =
    EventFields.Int("invocation_execution_total_ms", "Total time spent on executing invocation events in milliseconds.")
  private val INVOCATION_EXECUTION_TIME_50_US =
    EventFields.Int("invocation_execution_p50_us", "Median execution time of an invocation events in microseconds.")
  private val INVOCATION_EXECUTION_TIME_95_TO_50 =
    EventFields.Float("invocation_execution_p95_to_p50", "Relation of 95-th percentile of an invocation event execution to the median")
  private val INVOCATION_EXECUTION_TIME_99_TO_50 =
    EventFields.Float("invocation_execution_p99_to_p50", "Relation of 99-th percentile of an invocation event execution to the median")

  private val WRITE_LOCK_EVENTS = EventFields.Int("write_lock_events_count", "Number of requests for write lock")

  private val WRITE_LOCK_WAITING_TIME_TOTAL_MS =
    EventFields.Int("write_lock_waiting_ms", "Total time spent on waiting for acquisition of the write lock in milliseconds.")
  private val WRITE_LOCK_WAITING_TIME_50_US =
    EventFields.Int("write_lock_waiting_p50_us", "Median waiting time for the write lock in microseconds.")
  private val WRITE_LOCK_WAITING_TIME_95_TO_50 =
    EventFields.Float("write_lock_waiting_p95_to_p50", "Relation of 95-th percentile of a write lock acquisition to the median")
  private val WRITE_LOCK_WAITING_TIME_99_TO_50 =
    EventFields.Float("write_lock_waiting_p99_to_p50", "Relation of 99-th percentile of a write lock acquisition to the median")

  private val WRITE_LOCK_EXECUTION_TIME_TOTAL_MS =
    EventFields.Int("write_lock_execution_ms", "Total time spent on execution of write actions in milliseconds.")
  private val WRITE_LOCK_EXECUTION_TIME_50_US =
    EventFields.Int("write_lock_execution_p50_us", "Median execution time of write actions in microseconds.")
  private val WRITE_LOCK_EXECUTION_TIME_95_TO_50 =
    EventFields.Float("write_lock_execution_p95_to_p50", "Relation of 95-th percentile of a write action execution time to the median")
  private val WRITE_LOCK_EXECUTION_TIME_99_TO_50 =
    EventFields.Float("write_lock_execution_p99_to_p50", "Relation of 99-th percentile of a write action execution time to the median")

  private val READING_LOCK_EVENTS = EventFields.Int("reading_lock_events_count", "Number of events for read and write-intent locks")

  private val READING_LOCK_WAITING_TIME_TOTAL_MS =
    EventFields.Int("reading_lock_waiting_ms", "Total time spent on waiting for read and write-intent locks in milliseconds.")
  private val READING_LOCK_WAITING_TIME_50_US =
    EventFields.Int("reading_lock_waiting_p50_us", "Median waiting time for the read and write-intent locks in microseconds.")
  private val READING_LOCK_WAITING_TIME_95_TO_50 = EventFields.Float("reading_lock_waiting_p95_to_p50",
                                                                     "Relation of 95-th percentile of read and write-intent locks waiting to the median")
  private val READING_LOCK_WAITING_TIME_99_TO_50 = EventFields.Float("reading_lock_waiting_p99_to_p50",
                                                                     "Relation of 99-th percentile of read and write-intent locks waiting to the median")

  private val READING_LOCK_EXECUTION_TIME_TOTAL_MS =
    EventFields.Int("reading_lock_execution_ms", "Total time spent on execution of read and write-intent actions in milliseconds.")
  private val READING_LOCK_EXECUTION_TIME_50_US =
    EventFields.Int("reading_lock_execution_p50_us", "Median execution time of read and write-intent actions in microseconds.")
  private val READING_LOCK_EXECUTION_TIME_95_TO_50 = EventFields.Float("reading_lock_execution_p95_to_p50",
                                                                       "Relation of 95-th percentile of read and write-intent actions execution time to the median")
  private val READING_LOCK_EXECUTION_TIME_99_TO_50 = EventFields.Float("reading_lock_execution_p99_to_p50",
                                                                       "Relation of 99-th percentile of read and write-intent actions execution time to the median")

  private val UI_RESPONSIVENESS = GROUP.registerVarargEvent(
    "ui.responsiveness",
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
    READING_LOCK_EXECUTION_TIME_99_TO_50
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
      *reportDurationHistograms(totalExecution,
                                UI_EXECUTION_TIME_TOTAL_MS,
                                UI_EXECUTION_TIME_50_US,
                                UI_EXECUTION_TIME_95_TO_50,
                                UI_EXECUTION_TIME_99_TO_50),
      INVOCATION_EVENTS_COUNT.with(invocationEventsWaiting.totalCount),
      *reportDurationHistograms(invocationEventsWaiting,
                                INVOCATION_WAITING_TIME_TOTAL_MS,
                                INVOCATION_WAITING_TIME_50_US,
                                INVOCATION_WAITING_TIME_95_TO_50,
                                INVOCATION_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(invocationEventsExecution,
                                INVOCATION_EXECUTION_TIME_TOTAL_MS,
                                INVOCATION_EXECUTION_TIME_50_US,
                                INVOCATION_EXECUTION_TIME_95_TO_50,
                                INVOCATION_EXECUTION_TIME_99_TO_50),
      // some write lock requests may be upgraded from write-intent lock
      // we don't think it is useful to know the exact number of events that are blocked on write lock acquisition
      WRITE_LOCK_EVENTS.with(writeLockExecution.totalCount),
      *reportDurationHistograms(writeLockWaiting,
                                WRITE_LOCK_WAITING_TIME_TOTAL_MS,
                                WRITE_LOCK_WAITING_TIME_50_US,
                                WRITE_LOCK_WAITING_TIME_95_TO_50,
                                WRITE_LOCK_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(writeLockExecution,
                                WRITE_LOCK_EXECUTION_TIME_TOTAL_MS,
                                WRITE_LOCK_EXECUTION_TIME_50_US,
                                WRITE_LOCK_EXECUTION_TIME_95_TO_50,
                                WRITE_LOCK_EXECUTION_TIME_99_TO_50),
      READING_LOCK_EVENTS.with(readingLockExecution.totalCount),
      *reportDurationHistograms(readingLockWaiting,
                                READING_LOCK_WAITING_TIME_TOTAL_MS,
                                READING_LOCK_WAITING_TIME_50_US,
                                READING_LOCK_WAITING_TIME_95_TO_50,
                                READING_LOCK_WAITING_TIME_99_TO_50),
      *reportDurationHistograms(readingLockExecution,
                                READING_LOCK_EXECUTION_TIME_TOTAL_MS,
                                READING_LOCK_EXECUTION_TIME_50_US,
                                READING_LOCK_EXECUTION_TIME_95_TO_50,
                                READING_LOCK_EXECUTION_TIME_99_TO_50),
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
    1 * 1024, // 1g
    5 * 256, 6 * 256, 7 * 256, 8 * 256, // 2g
    9 * 256, 10 * 256, 11 * 256, 12 * 256, // 3g
    7 * 512, 8 * 512, // 4g
    9 * 512, 10 * 512, // 5g
    6 * 1024,
    7 * 1024,
    8 * 1024,
    9 * 1024,
    10 * 1024,
    11 * 1024,
    12 * 1024,
    13 * 1024,
    14 * 1024,
    15 * 1024,
    16 * 1024,
  )
  private val MEM_XMX_FIELD = EventFields.BoundedInt("xmx", intArrayOf(512, 768, 1024, 1536, 2048, 4096, 6000, 8192, 12288, 16384))
  private val MEM_SAMPLES_FIELD = EventFields.Int("samples")
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
    MEM_XMX_FIELD, MEM_SAMPLES_FIELD, MEM_HISTOGRAM_TOTAL1_FIELD, MEM_HISTOGRAM_TOTAL2_FIELD
  )

  @Deprecated("Not reported anymore")
  @JvmField
  internal val SLOW_OPERATIONS_ISSUES = GROUP.registerEvent(
    "slow.operation.issues",
    EventFields.StringListValidatedByInlineRegexp("issue_id", "[A-Z]{2,7}-\\d{1,6}")
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

/** Converts accumulated value into diff-value */
internal class LongDiffer(var previousAccumulatedValue: Long = 0) {
  /** @return diff between newAccumulatedValue and previous accumulated value, and updates the previous accumulated value */
  fun toDiff(newAccumulatedValue: Long): Long {
    val diff = newAccumulatedValue - previousAccumulatedValue
    previousAccumulatedValue = newAccumulatedValue
    return diff
  }

  override fun toString(): String = "LongDiffer[accumulated: $previousAccumulatedValue]"
}
