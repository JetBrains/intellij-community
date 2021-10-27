// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dto.toJsonStatistics
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics
import org.jetbrains.annotations.ApiStatus
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

  private val stages = MultiMap<Stage, Pair<StageEvent, Instant>>()

  init {
    synchronized(stages) {
      for (stage in Stage.values()) {
        stages.putValues(stage, SmartList())
      }
    }
  }

  fun addScanningStatistics(statistics: ScanningStatistics) {
    scanningStatistics += statistics.toJsonStatistics()
  }

  fun addProviderStatistics(statistics: IndexingJobStatistics) {
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

  fun startStage(stage: Stage) {
    synchronized(stages) {
      stages[stage].add(Pair(StageEvent.Started, Instant.now()))
    }
  }

  fun stopStage(stage: Stage) {
    synchronized(stages) {
      stages[stage].add(Pair(StageEvent.Stopped, Instant.now()))
    }
  }

  fun suspendStages() {
    val pause = Pair(StageEvent.Suspended, Instant.now())
    for (stage in Stage.values()) {
      stages[stage].add(pause)
    }
  }

  fun stopSuspendingStages() {
    val unpause = Pair(StageEvent.Unsuspended, Instant.now())
    synchronized(stages) {
      for (stage in Stage.values()) {
        stages[stage].add(unpause)
      }
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

  private fun writeStagesToDurations() {
    synchronized(stages) {
      var suspendedDuration: Duration? = Duration.ZERO
      var suspensionStart: Instant? = null
      for (stage in Stage.values()) {
        val events = stages[stage]
        var duration = Duration.ZERO
        var start: Instant? = null
        var isSuspended = false
        for (event in events) {
          when (event.first) {
            StageEvent.Started -> {
              log.assertTrue(start == null, "$stage is already started. Events $events")
              start = event.second
            }
            StageEvent.Suspended -> {
              if (suspendedDuration != null) {
                suspensionStart = event.second
              }
              if (start != null) {
                duration = duration.plus(Duration.between(start, event.second))
                start = null
                isSuspended = true
              }
            }
            StageEvent.Unsuspended -> {
              if (suspendedDuration != null) {
                log.assertTrue(suspensionStart != null, "Suspension was not started, but stopped. Events $events")
                suspendedDuration = suspendedDuration.plus(Duration.between(suspensionStart, event.second))
                suspensionStart = null
              }
              log.assertTrue(start == null, "$stage is not paused, tries to unpause. Events $events")
              if (isSuspended) {
                start = event.second
                isSuspended = false
              }
            }
            StageEvent.Stopped -> {
              log.assertTrue(start != null, "$stage is not started, tries to stop. Events $events")
              duration = duration.plus(Duration.between(start, event.second))
              start = null
            }
          }
        }
        stage.getProperty().set(timesImpl, duration)
        if (suspendedDuration != null) {
          timesImpl.suspendedDuration = suspendedDuration
          suspendedDuration = null
        }
      }
    }
  }

  /** Just a stage, don't have to cover whole indexing period, may intersect **/
  enum class Stage {
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

  enum class StageEvent {
    Started, Suspended, Unsuspended, Stopped
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
    override var scanFilesDuration: Duration = Duration.ZERO,
    override var suspendedDuration: Duration = Duration.ZERO,
    override var wasInterrupted: Boolean = false
  ): IndexingTimes

  data class SnapshotInputMappingStatsImpl(override var requests: Long, override var misses: Long): SnapshotInputMappingStats {
    override val hits: Long get() = requests - misses
  }
}