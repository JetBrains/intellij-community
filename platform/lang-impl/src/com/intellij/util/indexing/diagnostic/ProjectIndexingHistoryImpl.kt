// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonChangedFilesDuringIndexingStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dto.toJsonStatistics
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.concurrent.withLock
import kotlin.reflect.KMutableProperty1

private val indexingActivitySessionIdSequencer = AtomicLong()

@ApiStatus.Internal
data class ProjectScanningHistoryImpl(override val project: Project,
                                      override val scanningReason: String?,
                                      private val scanningType: ScanningType) : ProjectScanningHistory {
  companion object {
    private val scanningSessionIdSequencer = AtomicLong()
    private val log = thisLogger()

    fun startDumbModeBeginningTracking(project: Project,
                                       scanningHistory: ProjectScanningHistoryImpl): Runnable {
      val callback = Runnable {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        scanningHistory.createScanningDumbModeCallBack().accept(now)
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

  private val scanningStatisticsItems: AtomicReference<PersistentList<JsonScanningStatistics>> = AtomicReference(persistentListOf())
  override val scanningStatistics: List<JsonScanningStatistics> get() = scanningStatisticsItems.get()

  /**
   * Covers [currentDumbModeStart] and [events]
   */
  private val eventsLock = ReentrantLock()
  private val events = mutableListOf<Event>()
  private var currentDumbModeStart: ZonedDateTime? = null

  fun addScanningStatistics(statistics: ScanningStatistics) {
    val jsonStatistics = statistics.toJsonStatistics()
    scanningStatisticsItems.updateAndGet { it.add(jsonStatistics) }
  }

  private sealed interface Event {
    val instant: Instant

    data class StageEvent(val stage: Stage, val started: Boolean, override val instant: Instant) : Event
    data class SuspensionEvent(val started: Boolean, override val instant: Instant = Instant.now()) : Event
  }

  fun startStage(stage: Stage, instant: Instant) {
    eventsLock.withLock {
      events.add(Event.StageEvent(stage, true, instant))
    }
  }

  fun stopStage(stage: Stage, instant: Instant) {
    eventsLock.withLock {
      events.add(Event.StageEvent(stage, false, instant))
    }
  }

  fun suspendStages(instant: Instant): Unit = doSuspend(instant, true)

  fun stopSuspendingStages(instant: Instant): Unit = doSuspend(instant, false)

  private fun doSuspend(instant: Instant, start: Boolean) {
    eventsLock.withLock {
      events.add(Event.SuspensionEvent(start, instant))
    }
  }

  fun scanningStarted() {
    timesImpl.totalUpdatingTime = System.nanoTime()
    timesImpl.updatingStart = ZonedDateTime.now(ZoneOffset.UTC)
  }

  fun scanningFinished() {
    timesImpl.totalUpdatingTime = System.nanoTime() - timesImpl.totalUpdatingTime
    timesImpl.updatingEnd = timesImpl.updatingStart.plusNanos(timesImpl.totalUpdatingTime)

    eventsLock.withLock {
      currentDumbModeStart?.let {
        timesImpl.dumbModeStart = it
        stopStage(Stage.DumbMode, timesImpl.updatingEnd.toInstant())
        timesImpl.dumbModeWithPausesDuration = Duration.between(it, timesImpl.updatingEnd)
      }
    }

    writeStagesToDurations()
    timesImpl.concurrentHandlingSumOfThreadTimesWithPauses = Duration.ofNanos(scanningStatistics.sumOf { stat -> stat.totalOneThreadTimeWithPauses.nano })
    timesImpl.concurrentIterationAndScannersApplicationSumOfThreadTimesWithPauses = Duration.ofNanos(scanningStatistics.sumOf { stat -> stat.iterationAndScannersApplicationTime.nano })
    timesImpl.concurrentFileCheckSumOfThreadTimesWithPauses = Duration.ofNanos(scanningStatistics.sumOf { stat -> stat.filesCheckTime.nano })
    timesImpl.indexExtensionsDuration = Duration.ofNanos(
      scanningStatistics.sumOf { stat -> stat.timeIndexingWithoutContentViaInfrastructureExtension.nano })
  }

  fun setWasCancelled(reason: String?) {
    timesImpl.isCancelled = true
    timesImpl.cancellationReason = reason
  }

  private fun createScanningDumbModeCallBack(): Consumer<ZonedDateTime> = Consumer { now ->
    eventsLock.withLock {
      currentDumbModeStart = now
      startStage(Stage.DumbMode, now.toInstant())
    }
  }

  /**
   * Some StageEvent may appear between the beginning and end of suspension
   * because it actually takes place only on ProgressIndicator's check.
   * These normalizations move the moment of suspension start from declared to after all other events between it and suspension end:
   * suspended, event1, ..., eventN, unsuspended → event1, ..., eventN, suspended, unsuspended
   *
   * Suspended and unsuspended events appear only in pairs with none in between.
   */
  private fun getNormalizedEvents(): List<Event> {
    val normalizedEvents = mutableListOf<Event>()
    eventsLock.withLock {
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
              //speculate the start of suspension as the time of the last meaningful event if it ever happened
              if (suspensionStartTime == null) {
                suspensionStartTime = normalizedEvents.lastOrNull()?.instant
              }

              if (suspensionStartTime != null) { //the current observation may miss the start of suspension, see IDEA-281514
                normalizedEvents.add(Event.SuspensionEvent(true, suspensionStartTime))
                normalizedEvents.add(Event.SuspensionEvent(false, event.instant))
              }
              suspensionStartTime = null
            }
          }
          is Event.StageEvent -> {
            //the progressIndicator is checked before registering stages, so suspension has definitely ended before that moment;
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
    val startMap = hashMapOf<Stage, Instant>()
    val durationMap = hashMapOf<Stage, Duration>()
    for (stage in Stage.entries) {
      durationMap[stage] = Duration.ZERO
    }
    var suspendStart: Instant? = null

    for ((i, event) in normalizedEvents.withIndex()) {
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
            if (start != null) {
              durationMap[event.stage] = durationMap[event.stage]!!.plus(Duration.between(start, event.instant))
            }
            else if (event.stage == Stage.DumbMode) {
              val lastFinishOfDumbMode = normalizedEvents.subList(0, i).findLast { it is Event.StageEvent && it.stage == Stage.DumbMode && !it.started }
              val artificialStart = lastFinishOfDumbMode?.instant ?: times.updatingStart.toInstant()
              durationMap[event.stage] = durationMap[event.stage]!!.plus(Duration.between(artificialStart, event.instant))
            }
            else {
              log.error("${event.stage} is not started, tries to stop. Events $normalizedEvents")
            }
          }
        }
      }

      for (stage in Stage.entries) {
        stage.getProperty().set(timesImpl, durationMap[stage]!!)
      }
    }

    var start: Instant? = null
    var concurrentHandlingWallTimeWithPauses = Duration.ZERO
    for (event in normalizedEvents) {
      if (event !is Event.StageEvent || event.stage != Stage.CollectingIndexableFiles) {
        continue
      }
      if (event.started) {
        start = event.instant
      }
      else {
        log.assertTrue(start != null, "Concurrent handling is not started, tries to stop. Events $normalizedEvents")
        concurrentHandlingWallTimeWithPauses += Duration.between(start, event.instant)
      }
    }
    timesImpl.concurrentHandlingWallTimeWithPauses = concurrentHandlingWallTimeWithPauses
    timesImpl.pausedDuration = pausedDuration
  }

  /** Just a stage, don't have to cover the whole scanning period, may intersect **/
  enum class Stage {
    CreatingIterators {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::creatingIteratorsDuration
    },
    CollectingIndexableFiles {
      override fun getProperty(): KMutableProperty1<ScanningTimesImpl, Duration> = ScanningTimesImpl::concurrentHandlingWallTimeWithoutPauses
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
    override var updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var dumbModeStart: ZonedDateTime? = null,
    override var dumbModeWithPausesDuration: Duration = Duration.ZERO,
    override var dumbModeWithoutPausesDuration: Duration = Duration.ZERO,
    override var delayedPushPropertiesStageDuration: Duration = Duration.ZERO,
    override var creatingIteratorsDuration: Duration = Duration.ZERO,
    override var concurrentHandlingWallTimeWithoutPauses: Duration = Duration.ZERO,
    override var concurrentHandlingWallTimeWithPauses: Duration = Duration.ZERO,
    override var concurrentHandlingSumOfThreadTimesWithPauses: Duration = Duration.ZERO,
    override var concurrentIterationAndScannersApplicationSumOfThreadTimesWithPauses: Duration = Duration.ZERO,
    override var concurrentFileCheckSumOfThreadTimesWithPauses: Duration = Duration.ZERO,
    override var indexExtensionsDuration: Duration = Duration.ZERO,
    override var pausedDuration: Duration = Duration.ZERO,
    override var isCancelled: Boolean = false,
    override var cancellationReason: String? = null,
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
    // Convert to JSON to release memory occupied by statistic values.
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
        )
      }
      totalStats.totalNumberOfFiles += stats.numberOfFiles
      totalStats.totalNumberOfFilesIndexedByExtensions += stats.numberOfFilesIndexedByExtensions
      totalStats.totalBytes += stats.totalBytes
      totalStats.totalIndexValueChangerEvaluationTimeInAllThreads += stats.evaluateIndexValueChangerTime
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
    
    timesImpl.totalUpdatingTime = System.nanoTime() - timesImpl.totalUpdatingTime
    timesImpl.updatingEnd = timesImpl.updatingStart.plusNanos(timesImpl.totalUpdatingTime)
  }

  fun setWasCancelled(reason: String?) {
    timesImpl.isCancelled = true
    timesImpl.cancellationReason = reason
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
    override var totalBytes: NumberOfBytes,
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
    override val totalBytes: NumberOfBytes,
    override val processingTimeInAllThreads: TimeNano
  ) : BiggestFileTypeContributor

  data class StatsPerIndexerImpl(
    override var totalNumberOfFiles: Int,
    override var totalNumberOfFilesIndexedByExtensions: Int,
    override var totalBytes: NumberOfBytes,
    override var totalIndexValueChangerEvaluationTimeInAllThreads: TimeNano,
  ) : StatsPerIndexer

  data class DumbIndexingTimesImpl(
    override var scanningIds: LongSet = LongSet.of(),
    override val updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var contentLoadingVisibleDuration: Duration = Duration.ZERO,
    override var retrievingChangedDuringIndexingFilesDuration: Duration = Duration.ZERO,
    override var pausedDuration: Duration = Duration.ZERO,
    override var separateValueApplicationVisibleTime: TimeNano = 0,
    override var isCancelled: Boolean = false,
    override var cancellationReason: String? = null,
  ) : DumbIndexingTimes
}