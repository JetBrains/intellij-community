// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import java.time.Duration
import java.time.ZonedDateTime

typealias TimeMillis = Long
typealias TimeNano = Long
typealias BytesNumber = Long

/**
 * Extend this extension point to receive project scanning & indexing statistics
 * (e.g.: indexed file count, indexation speed, etc.) after each **dumb** indexation task was performed.
 */
interface ProjectIndexingHistoryListener {
  fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) = Unit

  fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory)
}

interface ProjectIndexingHistory {
  val project: Project
  val indexingReason: String?
  val indexingSessionId: Long
  val times: IndexingTimes
  val scanningStatistics: List<JsonScanningStatistics>
  val providerStatistics: List<JsonFileProviderIndexStatistics>
  val totalStatsPerFileType: Map<String, StatsPerFileType>
  val totalStatsPerIndexer: Map<String, StatsPerIndexer>
  val visibleTimeToAllThreadsTimeRatio: Double
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
  val totalIndexingTimeInAllThreads: TimeNano
  val snapshotInputMappingStats: SnapshotInputMappingStats
}

interface IndexingTimes {
  val indexingReason: String?
  val wasFullIndexing: Boolean
  val updatingStart: ZonedDateTime
  val totalUpdatingTime: TimeNano
  val updatingEnd: ZonedDateTime
  val indexingDuration: Duration
  val contentLoadingVisibleDuration: Duration
  val pushPropertiesDuration: Duration
  val indexExtensionsDuration: Duration
  var creatingIteratorsDuration: Duration
  val scanFilesDuration: Duration
  val suspendedDuration: Duration
  val wasInterrupted: Boolean
}

interface SnapshotInputMappingStats {
  val requests: Long
  val misses: Long
  val hits: Long
}