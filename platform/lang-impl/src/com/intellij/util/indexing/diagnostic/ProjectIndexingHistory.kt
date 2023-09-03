// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonChangedFilesDuringIndexingStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.messages.Topic
import it.unimi.dsi.fastutil.longs.LongSet
import java.time.Duration
import java.time.ZonedDateTime

typealias TimeMillis = Long
typealias TimeNano = Long
typealias BytesNumber = Long

/**
 * Extend this extension point to receive project scanning & indexing statistics
 * (e.g.: indexed file count, indexation speed, etc.) after each scanning or **dumb** indexation task was performed.
 */
interface ProjectIndexingActivityHistoryListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<ProjectIndexingActivityHistoryListener> = Topic(ProjectIndexingActivityHistoryListener::class.java,
                                                                     Topic.BroadcastDirection.NONE)
  }

  fun onStartedScanning(history: ProjectScanningHistory) {}

  fun onFinishedScanning(history: ProjectScanningHistory) {}

  fun onStartedDumbIndexing(history: ProjectDumbIndexingHistory) {}

  fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {}
}

interface ProjectIndexingActivityHistory {
  val project: Project
  val type: IndexDiagnosticDumper.IndexingActivityType
}


interface ProjectScanningHistory : ProjectIndexingActivityHistory {
  override val project: Project
  val indexingActivitySessionId: Long
  val scanningReason: String?
  val scanningSessionId: Long
  val times: ScanningTimes
  val scanningStatistics: List<JsonScanningStatistics>

  override val type: IndexDiagnosticDumper.IndexingActivityType
    get() = IndexDiagnosticDumper.IndexingActivityType.Scanning
}

interface ProjectDumbIndexingHistory : ProjectIndexingActivityHistory {
  override val project: Project
  val indexingActivitySessionId: Long
  val times: DumbIndexingTimes
  val changedDuringIndexingFilesStat: JsonChangedFilesDuringIndexingStatistics?
  val providerStatistics: List<JsonFileProviderIndexStatistics>
  val totalStatsPerFileType: Map<String, StatsPerFileType>
  val totalStatsPerIndexer: Map<String, StatsPerIndexer>
  val visibleTimeToAllThreadsTimeRatio: Double

  override val type: IndexDiagnosticDumper.IndexingActivityType
    get() = IndexDiagnosticDumper.IndexingActivityType.DumbIndexing
}

/**
 * isFull - if the whole project was rescanned (instead of a part of it)
 */
enum class ScanningType(val isFull: Boolean) {
  /**
   * Full project rescan forced by user via Repair IDE action
   */
  FULL_FORCED(true),

  /**
   * It's mandatory full project rescan on project open
   */
  FULL_ON_PROJECT_OPEN(true),

  /**
   * Full project rescan requested by some code
   */
  FULL(true),


  /**
   * Partial rescan forced by user via Repair IDE action on a limited scope (not full project)
   */
  PARTIAL_FORCED(false),

  /**
   * Partial project rescan requested by some code
   */
  PARTIAL(false),

  /**
   * Some files were considered changed and therefore rescanned
   */
  REFRESH(false);

  companion object {
    fun merge(first: ScanningType, second: ScanningType): ScanningType = returnFirstFound(first, second)

    private fun returnFirstFound(first: ScanningType, second: ScanningType): ScanningType {
      val types = listOf(FULL_FORCED, FULL_ON_PROJECT_OPEN, FULL, PARTIAL_FORCED, PARTIAL, REFRESH)
      for (type in types) {
        if (first == type || second == type) return type
      }
      throw IllegalStateException("Unexpected ScanningType $first $second")
    }
  }
}

interface StatsPerFileType {
  val totalNumberOfFiles: Int
  val totalBytes: BytesNumber
  val totalProcessingTimeInAllThreads: TimeNano
  val totalContentLoadingTimeInAllThreads: TimeNano
  val biggestFileTypeContributorList: List<BiggestFileTypeContributor>
}

interface BiggestFileTypeContributor {
  val providerName: String
  val numberOfFiles: Int
  val totalBytes: BytesNumber
  val processingTimeInAllThreads: TimeNano
}

interface StatsPerIndexer {
  val totalNumberOfFiles: Int
  val totalNumberOfFilesIndexedByExtensions: Int
  val totalBytes: BytesNumber
  val totalIndexValueChangerEvaluationTimeInAllThreads: TimeNano
}

interface ScanningTimes {
  val scanningReason: String?
  val scanningType: ScanningType
  val scanningId: Long
  val updatingStart: ZonedDateTime
  val totalUpdatingTime: TimeNano
  val updatingEnd: ZonedDateTime
  val dumbModeStart: ZonedDateTime?
  val dumbModeWithPausesDuration: Duration
  val dumbModeWithoutPausesDuration: Duration
  val delayedPushPropertiesStageDuration: Duration
  val indexExtensionsDuration: Duration
  var creatingIteratorsDuration: Duration
  val concurrentHandlingWallTimeWithoutPauses: Duration
  val concurrentHandlingWallTimeWithPauses: Duration
  val concurrentHandlingCPUTimeWithPauses: Duration
  val concurrentIterationAndScannersApplicationCPUTimeWithPauses: Duration
  val concurrentFileCheckCPUTimeWithPauses: Duration
  val pausedDuration: Duration
  val wasInterrupted: Boolean
}

interface DumbIndexingTimes {
  val scanningIds: LongSet
  val updatingStart: ZonedDateTime
  val totalUpdatingTime: TimeNano
  val updatingEnd: ZonedDateTime
  val contentLoadingVisibleDuration: Duration
  val retrievingChangedDuringIndexingFilesDuration: Duration
  val pausedDuration: Duration
  val appliedAllValuesSeparately: Boolean
  val separateValueApplicationVisibleTime: TimeNano
  val wasInterrupted: Boolean
}