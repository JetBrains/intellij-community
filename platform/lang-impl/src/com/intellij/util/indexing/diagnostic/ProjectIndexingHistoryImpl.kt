// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dto.toJsonStatistics
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KMutableProperty1

@ApiStatus.Internal
data class ProjectIndexingHistoryImpl(override val project: Project,
                                      override val indexingReason: String?,
                                      private val wasFullIndexing: Boolean) : ProjectIndexingHistory {
  private companion object {
    val indexingSessionIdSequencer = AtomicLong()
    val log = thisLogger()
  }

  override val indexingSessionId = indexingSessionIdSequencer.getAndIncrement()

  private val biggestContributorsPerFileTypeLimit = 10

  override val times: IndexingTimes by ::timesImpl

  private val timesImpl = IndexingTimesImpl(indexingReason = indexingReason, wasFullIndexing = wasFullIndexing,
                                            updatingStart = ZonedDateTime.now(ZoneOffset.UTC), totalUpdatingTime = System.nanoTime())

  override val scanningStatistics = arrayListOf<JsonScanningStatistics>()

  override val providerStatistics = arrayListOf<JsonFileProviderIndexStatistics>()

  override val totalStatsPerFileType = hashMapOf<String /* File type name */, StatsPerFileTypeImpl>()

  override val totalStatsPerIndexer = hashMapOf<String /* Index ID */, StatsPerIndexerImpl>()

  private val events = mutableListOf<Event>()

  fun addScanningStatistics(statistics: ScanningStatistics) {
    scanningStatistics += statistics.toJsonStatistics()
  }

  fun addProviderStatistics(statistics: IndexingFileSetStatistics) {
    // Convert to Json to release memory occupied by statistic values.
    providerStatistics += statistics.toJsonStatistics()

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
          totalIndexingTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(
            requests = 0,
            misses = 0
          )
        )
      }
      totalStats.totalNumberOfFiles += stats.numberOfFiles
      totalStats.totalNumberOfFilesIndexedByExtensions += stats.numberOfFilesIndexedByExtensions
      totalStats.totalBytes += stats.totalBytes
      totalStats.totalIndexingTimeInAllThreads += stats.indexingTime
    }
  }

  fun addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics: List<SnapshotInputMappingsStatistics>) {
    for (mappingsStatistic in snapshotInputMappingsStatistics) {
      val totalStats = totalStatsPerIndexer.getOrPut(mappingsStatistic.indexId.name) {
        StatsPerIndexerImpl(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexingTimeInAllThreads = 0,
          snapshotInputMappingStats = SnapshotInputMappingStatsImpl(requests = 0, misses = 0))
      }
      totalStats.snapshotInputMappingStats.requests += mappingsStatistic.totalRequests
      totalStats.snapshotInputMappingStats.misses += mappingsStatistic.totalMisses
    }
  }

  private sealed interface Event {
    val instant: Instant

    data class StageEvent(val stage: Stage, val started: Boolean, override val instant: Instant = Instant.now()) : Event
    data class SuspensionEvent(val started: Boolean, override val instant: Instant = Instant.now()) : Event
  }

  fun startStage(stage: Stage) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, true))
    }
  }

  fun stopStage(stage: Stage) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, false))
    }
  }

  fun suspendStages() {
    synchronized(events) {
      events.add(Event.SuspensionEvent(true))
    }
  }

  fun stopSuspendingStages() {
    synchronized(events) {
      events.add(Event.SuspensionEvent(false))
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

  /**
   * Some StageEvent may appear between begin and end of suspension, because it actually takes place only on ProgressIndicator's check.
   * This normalizations moves moment of suspension start from declared to after all other events between it and suspension end:
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
              log.assertTrue(suspensionStartTime == null, "Two suspension starts, no stops. Events $events")
              suspensionStartTime = event.instant
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
            normalizedEvents.add(event)
            if (suspensionStartTime != null) {
              //apparently we haven't stopped yet
              suspensionStartTime = event.instant
            }
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
      override fun getProperty() = IndexingTimesImpl::scanFilesDuration
    },

    Indexing {
      override fun getProperty() = IndexingTimesImpl::indexingDuration
    },

    PushProperties {
      override fun getProperty() = IndexingTimesImpl::pushPropertiesDuration
    };


    abstract fun getProperty(): KMutableProperty1<IndexingTimesImpl, Duration>
  }

  @TestOnly
  fun startStage(stage: Stage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, true, instant))
    }
  }

  @TestOnly
  fun stopStage(stage: Stage, instant: Instant) {
    synchronized(events) {
      events.add(Event.StageEvent(stage, false, instant))
    }
  }

  @TestOnly
  fun suspendStages(instant: Instant) {
    synchronized(events) {
      events.add(Event.SuspensionEvent(true, instant))
    }
  }

  @TestOnly
  fun stopSuspendingStages(instant: Instant) {
    synchronized(events) {
      events.add(Event.SuspensionEvent(false, instant))
    }
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
    override var totalIndexingTimeInAllThreads: TimeNano,
    override var snapshotInputMappingStats: SnapshotInputMappingStatsImpl
  ): StatsPerIndexer

  data class IndexingTimesImpl(
    override val indexingReason: String?,
    override val wasFullIndexing: Boolean,
    override val updatingStart: ZonedDateTime,
    override var totalUpdatingTime: TimeNano,
    override var updatingEnd: ZonedDateTime = updatingStart,
    override var indexingDuration: Duration = Duration.ZERO,
    override var contentLoadingDuration: Duration = Duration.ZERO,
    override var pushPropertiesDuration: Duration = Duration.ZERO,
    override var indexExtensionsDuration: Duration = Duration.ZERO,
    override var creatingIteratorsDuration: Duration = Duration.ZERO,
    override var scanFilesDuration: Duration = Duration.ZERO,
    override var suspendedDuration: Duration = Duration.ZERO,
    override var wasInterrupted: Boolean = false
  ): IndexingTimes

  data class SnapshotInputMappingStatsImpl(override var requests: Long, override var misses: Long): SnapshotInputMappingStats {
    override val hits: Long get() = requests - misses
  }
}