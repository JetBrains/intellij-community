// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.messages.Topic
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
  companion object {
    @Topic.AppLevel
    val TOPIC = Topic(ProjectIndexingHistoryListener::class.java, Topic.BroadcastDirection.NONE)
  }
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
    fun merge(first: ScanningType, second: ScanningType): ScanningType = returnFirstFound(first, second,
                                                                                          FULL_FORCED, FULL_ON_PROJECT_OPEN, FULL,
                                                                                          PARTIAL_FORCED, PARTIAL,
                                                                                          REFRESH)

    private fun returnFirstFound(first: ScanningType, second: ScanningType, vararg types: ScanningType): ScanningType {
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
  val snapshotInputMappingStats: SnapshotInputMappingStats
}

interface IndexingTimes {
  val indexingReason: String?
  val scanningType: ScanningType
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
  val appliedAllValuesSeparately: Boolean
  val separateValueApplicationVisibleTime: TimeNano
  val wasInterrupted: Boolean
}

interface SnapshotInputMappingStats {
  val requests: Long
  val misses: Long
  val hits: Long
}