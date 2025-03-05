// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup.IndexingActivityType
import com.intellij.util.indexing.diagnostic.dto.JsonChangedFilesDuringIndexingStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.messages.Topic
import it.unimi.dsi.fastutil.longs.LongSet
import java.time.Duration
import java.time.ZonedDateTime

typealias TimeMillis = Long
typealias TimeNano = Long
typealias NumberOfBytes = Long

/**
 * Extend this extension point to receive project scanning & indexing statistics
 * (e.g.: indexed file count, indexation speed, etc.) after each scanning or a **dumb** indexation task was performed.
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
  val type: IndexingActivityType
}


interface ProjectScanningHistory : ProjectIndexingActivityHistory {
  override val project: Project
  val indexingActivitySessionId: Long
  val scanningReason: String?
  val scanningSessionId: Long
  val times: ScanningTimes
  val scanningStatistics: List<JsonScanningStatistics>

  override val type: IndexingActivityType
    get() = IndexingActivityType.Scanning
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

  override val type: IndexingActivityType
    get() = IndexingActivityType.DumbIndexing
}

interface StatsPerFileType {
  val totalNumberOfFiles: Int
  val totalBytes: NumberOfBytes
  val totalProcessingTimeInAllThreads: TimeNano
  val totalContentLoadingTimeInAllThreads: TimeNano
  val biggestFileTypeContributorList: List<BiggestFileTypeContributor>
}

interface BiggestFileTypeContributor {
  val providerName: String
  val numberOfFiles: Int
  val totalBytes: NumberOfBytes
  val processingTimeInAllThreads: TimeNano
}

interface StatsPerIndexer {
  val totalNumberOfFiles: Int
  val totalNumberOfFilesIndexedByExtensions: Int
  val totalBytes: NumberOfBytes
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
  val concurrentHandlingSumOfThreadTimesWithPauses: Duration
  val concurrentIterationAndScannersApplicationSumOfThreadTimesWithPauses: Duration
  val concurrentFileCheckSumOfThreadTimesWithPauses: Duration
  val pausedDuration: Duration
  val isCancelled: Boolean
  val cancellationReason: String?
}

interface DumbIndexingTimes {
  val scanningIds: LongSet
  val updatingStart: ZonedDateTime
  val totalUpdatingTime: TimeNano
  val updatingEnd: ZonedDateTime
  val contentLoadingVisibleDuration: Duration
  val retrievingChangedDuringIndexingFilesDuration: Duration
  val pausedDuration: Duration
  val separateValueApplicationVisibleTime: TimeNano
  val isCancelled: Boolean
  val cancellationReason: String?
}