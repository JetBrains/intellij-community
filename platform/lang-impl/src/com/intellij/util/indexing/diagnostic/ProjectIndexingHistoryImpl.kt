// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonChangedFilesDuringIndexingStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dto.toJsonStatistics
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics
import it.unimi.dsi.fastutil.longs.LongSet
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.reflect.KMutableProperty1

@ApiStatus.Internal
data class ProjectIndexingHistoryImpl(override val project: Project,
                                      override val indexingReason: String?,
                                      private val scanningType: ScanningType) : ProjectIndexingHistory {
  private companion object {
    val indexingSessionIdSequencer = AtomicLong()
    val log = thisLogger()
  }

  override val indexingSessionId: Long = indexingSessionIdSequencer.getAndIncrement()

  private val biggestContributorsPerFileTypeLimit = 10

  override val times: IndexingTimes by ::timesImpl

  private val timesImpl = IndexingTimesImpl(indexingReason = indexingReason, scanningType = scanningType,
                                            updatingStart = ZonedDateTime.now(ZoneOffset.UTC), totalUpdatingTime = System.nanoTime())

  override val scanningStatistics: ArrayList<JsonScanningStatistics> = arrayListOf()

  override val providerStatistics: ArrayList<JsonFileProviderIndexStatistics> = arrayListOf()

  override val totalStatsPerFileType: HashMap<String, StatsPerFileTypeImpl> = hashMapOf()

  override val totalStatsPerIndexer: HashMap<String, StatsPerIndexerImpl> = hashMapOf()

  override var visibleTimeToAllThreadsTimeRatio: Double = 0.0

  private val events = mutableListOf<Event>()

  fun addScanningStatistics(statistics: ScanningStatistics) {
    scanningStatistics += statistics.toJsonStatistics()
  }

  fun addProviderStatistics(statistics: IndexingFileSetStatistics) {
    // Convert to Json to release memory occupied by statistic values.
    providerStatistics += statistics.toJsonStatistics(visibleTimeToAllThreadsTimeRatio)

    for ((fileType, fileTypeStats) in statistics.statsPerFileType) {
      val totalStats = totalStatsPerFileType.getOrPut(fileType) {
        StatsPerFileTypeImpl(0, 0, 0, 0,
                         LimitedPriorityQueue(biggestContributorsPerFileTypeLimit, compareBy { it.processingTimeInAllThreads }))
      }
      totalStats.totalNumberOfFiles += fileTypeStats.numberOfFiles
      totalStats.totalBytes += fileTypeStats.totalBytes
      totalStats.totalProcessingTimeInAllThreads += fileTypeStats.processingTimeInAllThreads
      totalStats.totalContentLoadingTimeInAllThreads += fileTypeStats.contentLoadingTimeInAllThreads
      totalStats.biggestFileTypeContributors.addElement(
        BiggestFileTypeContributorImpl(
          statistics.fileSetName,
          fileTypeStats.numberOfFiles,
          fileTypeStats.totalBytes,
          fileTypeStats.processingTimeInAllThreads
        )
      )
    }

    for ((indexId, stats) in statistics.statsPerIndexer) {
      val totalStats = totalStatsPerIndexer.getOrPut(indexId) {
        StatsPerIndexerImpl(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexValueChangerEvaluationTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(
            requests = 0,
            misses = 0
          )
        )
      }
      totalStats.totalNumberOfFiles += stats.numberOfFiles
      totalStats.totalNumberOfFilesIndexedByExtensions += stats.numberOfFilesIndexedByExtensions
      totalStats.totalBytes += stats.totalBytes
      totalStats.totalIndexValueChangerEvaluationTimeInAllThreads += stats.evaluateIndexValueChangerTime
    }
  }

  fun addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics: List<SnapshotInputMappingsStatistics>) {
    for (mappingsStatistic in snapshotInputMappingsStatistics) {
      val totalStats = totalStatsPerIndexer.getOrPut(mappingsStatistic.indexId.name) {
        StatsPerIndexerImpl(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexValueChangerEvaluationTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(requests = 0, misses = 0))
      }
      totalStats.snapshotInputMappingStats.requests += mappingsStatistic.totalRequests
      totalStats.snapshotInputMappingStats.misses += mappingsStatistic.totalMisses
    }
  }

  private sealed interface Event {
    val instant: Instant

    data class StageEvent(val stage: Stage, val started: Boolean, override val instant: Instant) : Event
    data class SuspensionEvent(val started: Boolean, override val instant: Instant) : Event
  }

  fun startStage(stage: Stage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, true, instant))
    }
  }

  fun stopStage(stage: Stage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, false, instant))
    }
  }

  fun suspendStages(instant: Instant): Unit = doSuspend(instant, true)

  fun stopSuspendingStages(instant: Instant): Unit = doSuspend(instant, false)

  private fun doSuspend(instant: Instant, start: Boolean) {
    synchronized(events) {
      events.add(Event.SuspensionEvent(start, instant))
    }
  }

  fun indexingFinished() {
    writeStagesToDurations()
  }

  fun setWasInterrupted(interrupted: Boolean) {
    timesImpl.wasInterrupted = interrupted
  }

  fun finishTotalUpdatingTime() {
    timesImpl.updatingEnd = ZonedDateTime.now(ZoneOffset.UTC)
    timesImpl.totalUpdatingTime = System.nanoTime() - timesImpl.totalUpdatingTime
  }

  fun setScanFilesDuration(duration: Duration) {
    timesImpl.scanFilesDuration = duration
  }

  fun createScanningDumbModeCallback(): Consumer<in ZonedDateTime> = Consumer { now ->
    startStage(Stage.DumbMode, now.toInstant())
  }

  /**
   * Some StageEvent may appear between begin and end of suspension, because it actually takes place only on ProgressIndicator's check.
   * These normalizations move moment of suspension start from declared to after all other events between it and suspension end:
   * suspended, event1, ..., eventN, unsuspended -> event1, ..., eventN, suspended, unsuspended
   *
   * Suspended and unsuspended events appear only on pairs with none in between.
   */
  private fun getNormalizedEvents(): List<Event> {
    val normalizedEvents = mutableListOf<Event>()
    synchronized(events) {
      var suspensionStartTime: Instant? = null
      for (event in events) {
        when (event) {
          is Event.SuspensionEvent -> {
            if (event.started) {
              if (suspensionStartTime == null) {
                suspensionStartTime = event.instant
              }
            }
            else {
              //speculate suspension start as time of last meaningful event, if it ever happened
              if (suspensionStartTime == null) {
                suspensionStartTime = normalizedEvents.lastOrNull()?.instant
              }

              if (suspensionStartTime != null) { //observation may miss the start of suspension, see IDEA-281514
                normalizedEvents.add(Event.SuspensionEvent(true, suspensionStartTime))
                normalizedEvents.add(Event.SuspensionEvent(false, event.instant))
              }
              suspensionStartTime = null
            }
          }
          is Event.StageEvent -> {
            if (suspensionStartTime != null) {
              normalizedEvents.add(Event.SuspensionEvent(true, suspensionStartTime))
              normalizedEvents.add(Event.SuspensionEvent(false, event.instant))
              suspensionStartTime = null
            }
            normalizedEvents.add(event)
          }
        }
      }
    }
    return normalizedEvents
  }

  private fun writeStagesToDurations() {
    val normalizedEvents = getNormalizedEvents()
    var suspendedDuration = Duration.ZERO
    val startMap = hashMapOf<Stage, Instant>()
    val durationMap = hashMapOf<Stage, Duration>()
    for (stage in Stage.values()) {
      durationMap[stage] = Duration.ZERO
    }
    var suspendStart: Instant? = null

    for (event in normalizedEvents) {
      when (event) {
        is Event.SuspensionEvent -> {
          if (event.started) {
            for (entry in startMap) {
              durationMap[entry.key] = durationMap[entry.key]!!.plus(Duration.between(entry.value, event.instant))
            }
            suspendStart = event.instant
          }
          else {
            if (suspendStart != null) {
              suspendedDuration = suspendedDuration.plus(Duration.between(suspendStart, event.instant))
              suspendStart = null
            }
            startMap.replaceAll { _, _ -> event.instant } //happens strictly after suspension start event, startMap shouldn't change
          }
        }
        is Event.StageEvent -> {
          if (event.started) {
            val oldStart = startMap.put(event.stage, event.instant)
            log.assertTrue(oldStart == null, "${event.stage} is already started. Events $normalizedEvents")
          }
          else {
            val start = startMap.remove(event.stage)
            log.assertTrue(start != null, "${event.stage} is not started, tries to stop. Events $normalizedEvents")
            durationMap[event.stage] = durationMap[event.stage]!!.plus(Duration.between(start, event.instant))
          }
        }
      }

      for (stage in Stage.values()) {
        stage.getProperty().set(timesImpl, durationMap[stage]!!)
      }
      timesImpl.suspendedDuration = suspendedDuration
    }
  }

  /** Just a stage, don't have to cover whole indexing period, may intersect **/
  enum class Stage {
    CreatingIterators {
      override fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration> = IndexingTimesImpl::creatingIteratorsDuration
    },
    Scanning {
      override fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration> = IndexingTimesImpl::scanFilesDuration
    },

    Indexing {
      override fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration> = IndexingTimesImpl::indexingDuration
    },

    PushProperties {
      override fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration> = IndexingTimesImpl::pushPropertiesDuration
    },

    DumbMode {
      override fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration> = IndexingTimesImpl::dumbModeDuration
    };


    abstract fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration>
  }

  data class StatsPerFileTypeImpl(
    override var totalNumberOfFiles: Int,
    override var totalBytes: BytesNumber,
    override var totalProcessingTimeInAllThreads: TimeNano,
    override var totalContentLoadingTimeInAllThreads: TimeNano,
    val biggestFileTypeContributors: LimitedPriorityQueue<BiggestFileTypeContributorImpl>
  ): StatsPerFileType {
    override val biggestFileTypeContributorList: List<BiggestFileTypeContributor>
      get() = biggestFileTypeContributors.biggestElements
  }

  data class BiggestFileTypeContributorImpl(
    override val providerName: String,
    override val numberOfFiles: Int,
    override val totalBytes: BytesNumber,
    override val processingTimeInAllThreads: TimeNano
  ): BiggestFileTypeContributor

  data class StatsPerIndexerImpl(
    override var totalNumberOfFiles: Int,
    override var totalNumberOfFilesIndexedByExtensions: Int,
    override var totalBytes: BytesNumber,
    override var totalIndexValueChangerEvaluationTimeInAllThreads: TimeNano,
    override var snapshotInputMappingStats: SnapshotInputMappingStatsImpl
  ): StatsPerIndexer

  data class IndexingTimesImpl(
    override val indexingReason: String?,
    override val scanningType: ScanningType,
    override val updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var indexingDuration: Duration = Duration.ZERO,
    override var contentLoadingVisibleDuration: Duration = Duration.ZERO,
    override var pushPropertiesDuration: Duration = Duration.ZERO,
    override var indexExtensionsDuration: Duration = Duration.ZERO,
    override var creatingIteratorsDuration: Duration = Duration.ZERO,
    override var scanFilesDuration: Duration = Duration.ZERO,
    override var suspendedDuration: Duration = Duration.ZERO,
    override var appliedAllValuesSeparately: Boolean = true,
    override var separateValueApplicationVisibleTime: TimeNano = 0,
    override var wasInterrupted: Boolean = false,

    var dumbModeDuration: Duration = Duration.ZERO //just to have the same effect on pause time in old and new diagnostics
  ) : IndexingTimes

  data class SnapshotInputMappingStatsImpl(override var requests: Long, override var misses: Long) : SnapshotInputMappingStats {
    override val hits: Long get() = requests - misses
  }
}

private val indexingActivitySessionIdSequencer = AtomicLong()

@ApiStatus.Internal
data class ProjectScanningHistoryImpl(override val project: Project,
                                      override val scanningReason: String?,
                                      private val scanningType: ScanningType) : ProjectScanningHistory {
  companion object {
    private val scanningSessionIdSequencer = AtomicLong()
    private val log = thisLogger()

    fun startDumbModeBeginningTracking(project: Project,
                                       scanningHistory: ProjectScanningHistoryImpl,
                                       oldHistory: ProjectIndexingHistoryImpl): Runnable {
      val now = ZonedDateTime.now(ZoneOffset.UTC)
      val callback = Runnable {
        scanningHistory.createScanningDumbModeCallBack().andThen(oldHistory.createScanningDumbModeCallback()).accept(now)
      }
      ReadAction.run<RuntimeException> {
        if (!project.isDisposed) {
          project.getService(DumbModeFromScanningTrackerService::class.java).setScanningDumbModeStartCallback(callback)
        }
      }
      return callback
    }

    fun finishDumbModeBeginningTracking(project: Project) {
      ReadAction.run<RuntimeException> {
        if (!project.isDisposed) {
          project.getService(DumbModeFromScanningTrackerService::class.java).cleanScanningDumbModeStartCallback()
        }
      }
    }
  }

  override val indexingActivitySessionId: Long = indexingActivitySessionIdSequencer.getAndIncrement()

  override val scanningSessionId: Long = scanningSessionIdSequencer.getAndIncrement()

  override val times: ScanningTimes by ::timesImpl

  private val timesImpl = ScanningTimesImpl(scanningReason = scanningReason, scanningType = scanningType, scanningId = scanningSessionId,
                                            updatingStart = ZonedDateTime.now(ZoneOffset.UTC), totalUpdatingTime = System.nanoTime())

  override val scanningStatistics: ArrayList<JsonScanningStatistics> = arrayListOf()

  private val events = mutableListOf<Event>()

  @Volatile
  private var currentDumbModeStart: ZonedDateTime? = null

  fun addScanningStatistics(statistics: ScanningStatistics) {
    scanningStatistics += statistics.toJsonStatistics()
  }

  private sealed interface Event {
    val instant: Instant

    data class StageEvent(val stage: ScanningStage, val started: Boolean, override val instant: Instant) : Event
    data class SuspensionEvent(val started: Boolean, override val instant: Instant = Instant.now()) : Event
  }

  fun startStage(stage: ScanningStage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, true, instant))
    }
  }

  fun stopStage(stage: ScanningStage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, false, instant))
    }
  }

  fun suspendStages(instant: Instant): Unit = doSuspend(instant, true)

  fun stopSuspendingStages(instant: Instant): Unit = doSuspend(instant, false)

  private fun doSuspend(instant: Instant, start: Boolean) {
    synchronized(events) {
      events.add(Event.SuspensionEvent(start, instant))
    }
  }

  fun scanningFinished() {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    timesImpl.updatingEnd = now
    timesImpl.totalUpdatingTime = System.nanoTime() - timesImpl.totalUpdatingTime

    currentDumbModeStart?.let {
      timesImpl.dumbModeStart = it
      stopStage(ScanningStage.DumbMode, now.toInstant())
      timesImpl.dumbModeWithPausesDuration = Duration.between(it, timesImpl.updatingEnd)
    }

    writeStagesToDurations()
    timesImpl.indexExtensionsDuration = scanningStatistics.sumOf { stat -> stat.timeIndexingWithoutContentViaInfrastructureExtension.nano }.let {
      Duration.ofNanos(it)
    }
  }

  fun setWasInterrupted() {
    timesImpl.wasInterrupted = true
  }

  private fun createScanningDumbModeCallBack(): Consumer<ZonedDateTime> = Consumer { now ->
    currentDumbModeStart = now
    startStage(ScanningStage.DumbMode, now.toInstant())
  }

  /**
   * Some StageEvent may appear between begin and end of suspension, because it actually takes place only on ProgressIndicator's check.
   * These normalizations move moment of suspension start from declared to after all other events between it and suspension end:
   * suspended, event1, ..., eventN, unsuspended -> event1, ..., eventN, suspended, unsuspended
   *
   * Suspended and unsuspended events appear only on pairs with none in between.
   */
  private fun getNormalizedEvents(): List<Event> {
    val normalizedEvents = mutableListOf<Event>()
    synchronized(events) {
      var suspensionStartTime: Instant? = null
      for (event in events) {
        when (event) {
          is Event.SuspensionEvent -> {
            if (event.started) {
              if (suspensionStartTime == null) {
                suspensionStartTime = event.instant
              }
            }
            else {
              //speculate suspension start as time of last meaningful event, if it ever happened
              if (suspensionStartTime == null) {
                suspensionStartTime = normalizedEvents.lastOrNull()?.instant
              }

              if (suspensionStartTime != null) { //observation may miss the start of suspension, see IDEA-281514
                normalizedEvents.add(Event.SuspensionEvent(true, suspensionStartTime))
                normalizedEvents.add(Event.SuspensionEvent(false, event.instant))
              }
              suspensionStartTime = null
            }
          }
          is Event.StageEvent -> {
            //progressIndicator is checked before registering stages, so suspension has definitely ended before that moment;
            //event may be registered later, but the milliseconds of difference are not that important
            if (suspensionStartTime != null) {
              normalizedEvents.add(Event.SuspensionEvent(true, suspensionStartTime))
              normalizedEvents.add(Event.SuspensionEvent(false, event.instant))
              suspensionStartTime = null
            }
            normalizedEvents.add(event)
          }
        }
      }
    }
    return normalizedEvents
  }

  private fun writeStagesToDurations() {
    val normalizedEvents = getNormalizedEvents()
    var pausedDuration = Duration.ZERO
    val startMap = hashMapOf<ScanningStage, Instant>()
    val durationMap = hashMapOf<ScanningStage, Duration>()
    for (stage in ScanningStage.values()) {
      durationMap[stage] = Duration.ZERO
    }
    var suspendStart: Instant? = null

    for (event in normalizedEvents) {
      when (event) {
        is Event.SuspensionEvent -> {
          if (event.started) {
            for (entry in startMap) {
              durationMap[entry.key] = durationMap[entry.key]!!.plus(Duration.between(entry.value, event.instant))
            }
            suspendStart = event.instant
          }
          else {
            if (suspendStart != null) {
              pausedDuration = pausedDuration.plus(Duration.between(suspendStart, event.instant))
              suspendStart = null
            }
            startMap.replaceAll { _, _ -> event.instant } //happens strictly after suspension start event, startMap shouldn't change
          }
        }
        is Event.StageEvent -> {
          if (event.started) {
            val oldStart = startMap.put(event.stage, event.instant)
            log.assertTrue(oldStart == null, "${event.stage} is already started. Events $normalizedEvents")
          }
          else {
            val start = startMap.remove(event.stage)
            log.assertTrue(start != null, "${event.stage} is not started, tries to stop. Events $normalizedEvents")
            durationMap[event.stage] = durationMap[event.stage]!!.plus(Duration.between(start, event.instant))
          }
        }
      }

      for (stage in ScanningStage.values()) {
        stage.getProperty().set(timesImpl, durationMap[stage]!!)
      }
    }
    timesImpl.pausedDuration = pausedDuration
  }

  /** Just a stage, don't have to cover whole indexing period, may intersect **/
  enum class ScanningStage {
    CreatingIterators {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::creatingIteratorsDuration
    },
    CollectingIndexableFiles {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::collectingIndexableFilesDuration
    },
    DelayedPushProperties {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::delayedPushPropertiesStageDuration
    },
    DumbMode {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::dumbModeWithoutPausesDuration
    };

    abstract fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration>
  }

  data class ScanningTimesImpl(
    override val scanningReason: String?,
    override val scanningType: ScanningType,
    override val scanningId: Long,
    override val updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var dumbModeStart: ZonedDateTime? = null,
    override var dumbModeWithPausesDuration: Duration = Duration.ZERO,
    override var dumbModeWithoutPausesDuration: Duration = Duration.ZERO,
    override var delayedPushPropertiesStageDuration: Duration = Duration.ZERO,
    override var creatingIteratorsDuration: Duration = Duration.ZERO,
    override var collectingIndexableFilesDuration: Duration = Duration.ZERO,
    override var indexExtensionsDuration: Duration = Duration.ZERO,
    override var pausedDuration: Duration = Duration.ZERO,
    override var wasInterrupted: Boolean = false
  ) : ScanningTimes
}

@ApiStatus.Internal
data class ProjectDumbIndexingHistoryImpl(override val project: Project) : ProjectDumbIndexingHistory {

  override val indexingActivitySessionId: Long = indexingActivitySessionIdSequencer.getAndIncrement()

  private val biggestContributorsPerFileTypeLimit = 10

  override val times: DumbIndexingTimes by ::timesImpl

  private val timesImpl = DumbIndexingTimesImpl(updatingStart = ZonedDateTime.now(ZoneOffset.UTC), totalUpdatingTime = System.nanoTime())

  override var changedDuringIndexingFilesStat: JsonChangedFilesDuringIndexingStatistics = JsonChangedFilesDuringIndexingStatistics()

  override val providerStatistics: ArrayList<JsonFileProviderIndexStatistics> = arrayListOf()

  override val totalStatsPerFileType: HashMap<String, StatsPerFileTypeImpl> = hashMapOf()

  override val totalStatsPerIndexer: HashMap<String, StatsPerIndexerImpl> = hashMapOf()

  override var visibleTimeToAllThreadsTimeRatio: Double = 0.0

  private var events = mutableListOf<SuspensionEvent>()

  fun setChangedFilesDuringIndexingStatistics(statistics: ChangedFilesDuringIndexingStatistics) {
    changedDuringIndexingFilesStat = statistics.toJsonStatistics()
    timesImpl.retrievingChangedDuringIndexingFilesDuration = Duration.ofNanos(statistics.retrievingTime)
  }

  fun addProviderStatistics(statistics: IndexingFileSetStatistics) {
    // Convert to Json to release memory occupied by statistic values.
    providerStatistics += statistics.toJsonStatistics(visibleTimeToAllThreadsTimeRatio)

    for ((fileType, fileTypeStats) in statistics.statsPerFileType) {
      val totalStats = totalStatsPerFileType.getOrPut(fileType) {
        StatsPerFileTypeImpl(0, 0, 0, 0,
                             LimitedPriorityQueue(biggestContributorsPerFileTypeLimit, compareBy { it.processingTimeInAllThreads }),
                             fileTypeStats.parentLanguages)
      }
      totalStats.totalNumberOfFiles += fileTypeStats.numberOfFiles
      totalStats.totalBytes += fileTypeStats.totalBytes
      totalStats.totalProcessingTimeInAllThreads += fileTypeStats.processingTimeInAllThreads
      totalStats.totalContentLoadingTimeInAllThreads += fileTypeStats.contentLoadingTimeInAllThreads
      totalStats.biggestFileTypeContributors.addElement(
        BiggestFileTypeContributorImpl(
          statistics.fileSetName,
          fileTypeStats.numberOfFiles,
          fileTypeStats.totalBytes,
          fileTypeStats.processingTimeInAllThreads
        )
      )
    }

    for ((indexId, stats) in statistics.statsPerIndexer) {
      val totalStats = totalStatsPerIndexer.getOrPut(indexId) {
        StatsPerIndexerImpl(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexValueChangerEvaluationTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(
            requests = 0,
            misses = 0
          )
        )
      }
      totalStats.totalNumberOfFiles += stats.numberOfFiles
      totalStats.totalNumberOfFilesIndexedByExtensions += stats.numberOfFilesIndexedByExtensions
      totalStats.totalBytes += stats.totalBytes
      totalStats.totalIndexValueChangerEvaluationTimeInAllThreads += stats.evaluateIndexValueChangerTime
    }
  }

  fun addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics: List<SnapshotInputMappingsStatistics>) {
    for (mappingsStatistic in snapshotInputMappingsStatistics) {
      val totalStats = totalStatsPerIndexer.getOrPut(mappingsStatistic.indexId.name) {
        StatsPerIndexerImpl(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexValueChangerEvaluationTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(requests = 0, misses = 0))
      }
      totalStats.snapshotInputMappingStats.requests += mappingsStatistic.totalRequests
      totalStats.snapshotInputMappingStats.misses += mappingsStatistic.totalMisses
    }
  }

  data class SuspensionEvent(val started: Boolean, val instant: Instant)

  fun suspendStages(instant: Instant): Unit = registerSuspension(true, instant)

  fun stopSuspendingStages(instant: Instant): Unit = registerSuspension(false, instant)

  private fun registerSuspension(started: Boolean, instant: Instant) {
    synchronized(events) {
      events.add(SuspensionEvent(started, instant))
    }
  }

  fun setScanningIds(ids: LongSet){
    timesImpl.scanningIds = ids
  }

  fun indexingFinished() {
    writeStagesToDurations()
  }

  fun setWasInterrupted() {
    timesImpl.wasInterrupted = true
  }

  fun finishTotalUpdatingTime() {
    timesImpl.updatingEnd = ZonedDateTime.now(ZoneOffset.UTC)
    timesImpl.totalUpdatingTime = System.nanoTime() - timesImpl.totalUpdatingTime
  }

  private fun writeStagesToDurations() {
    var suspendStart: Instant? = timesImpl.updatingStart.toInstant()
    var suspendedDuration = Duration.ZERO

    for (event in events) {
      if (event.started) {
        suspendStart = event.instant
      }
      else if (suspendStart != null) {
        suspendedDuration += Duration.between(suspendStart, event.instant)
        suspendStart = null
      }
    }
    timesImpl.pausedDuration = suspendedDuration
  }

  data class StatsPerFileTypeImpl(
    override var totalNumberOfFiles: Int,
    override var totalBytes: BytesNumber,
    override var totalProcessingTimeInAllThreads: TimeNano,
    override var totalContentLoadingTimeInAllThreads: TimeNano,
    val biggestFileTypeContributors: LimitedPriorityQueue<BiggestFileTypeContributorImpl>,
    val parentLanguages: List<String>
  ) : StatsPerFileType {
    override val biggestFileTypeContributorList: List<BiggestFileTypeContributor>
      get() = biggestFileTypeContributors.biggestElements
  }

  data class BiggestFileTypeContributorImpl(
    override val providerName: String,
    override val numberOfFiles: Int,
    override val totalBytes: BytesNumber,
    override val processingTimeInAllThreads: TimeNano
  ) : BiggestFileTypeContributor

  data class StatsPerIndexerImpl(
    override var totalNumberOfFiles: Int,
    override var totalNumberOfFilesIndexedByExtensions: Int,
    override var totalBytes: BytesNumber,
    override var totalIndexValueChangerEvaluationTimeInAllThreads: TimeNano,
    override var snapshotInputMappingStats: SnapshotInputMappingStatsImpl
  ) : StatsPerIndexer

  data class DumbIndexingTimesImpl(
    override var scanningIds: LongSet = LongSet.of(),
    override val updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var contentLoadingVisibleDuration: Duration = Duration.ZERO,
    override var retrievingChangedDuringIndexingFilesDuration: Duration = Duration.ZERO,
    override var pausedDuration: Duration = Duration.ZERO,
    override var appliedAllValuesSeparately: Boolean = true,
    override var separateValueApplicationVisibleTime: TimeNano = 0,
    override var wasInterrupted: Boolean = false
  ) : DumbIndexingTimes

  data class SnapshotInputMappingStatsImpl(override var requests: Long, override var misses: Long) : SnapshotInputMappingStats {
    override val hits: Long get() = requests - misses
  }
}