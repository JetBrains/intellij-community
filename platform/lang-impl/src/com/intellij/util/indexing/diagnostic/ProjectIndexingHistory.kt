// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dto.toJsonStatistics
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

typealias TimeMillis = Long
typealias TimeNano = Long
typealias BytesNumber = Long

data class ProjectIndexingHistory(val project: Project) {

  private companion object {
    val indexingSessionIdSequencer = AtomicLong()
  }

  val indexingSessionId = indexingSessionIdSequencer.getAndIncrement()

  private val biggestContributorsPerFileTypeLimit = 10

  val times = IndexingTimes(updatingStart = ZonedDateTime.now(ZoneOffset.UTC), totalUpdatingTime = System.nanoTime())

  val scanningStatistics = arrayListOf<JsonScanningStatistics>()

  val providerStatistics = arrayListOf<JsonFileProviderIndexStatistics>()

  val totalStatsPerFileType = hashMapOf<String /* File type name */, StatsPerFileType>()

  val totalStatsPerIndexer = hashMapOf<String /* Index ID */, StatsPerIndexer>()

  fun addScanningStatistics(statistics: ScanningStatistics) {
    scanningStatistics += statistics.toJsonStatistics()
  }

  fun addProviderStatistics(statistics: IndexingJobStatistics) {
    // Convert to Json to release memory occupied by statistic values.
    providerStatistics += statistics.toJsonStatistics()

    for ((fileType, fileTypeStats) in statistics.statsPerFileType) {
      val totalStats = totalStatsPerFileType.getOrPut(fileType) {
        StatsPerFileType(0, 0, 0, 0,
                         LimitedPriorityQueue(biggestContributorsPerFileTypeLimit, compareBy { it.indexingTimeInAllThreads }))
      }
      totalStats.totalNumberOfFiles += fileTypeStats.numberOfFiles
      totalStats.totalBytes += fileTypeStats.totalBytes
      totalStats.totalIndexingTimeInAllThreads += fileTypeStats.indexingTime.sumTime
      totalStats.totalContentLoadingTimeInAllThreads += fileTypeStats.contentLoadingTime.sumTime
      totalStats.biggestFileTypeContributors.addElement(
        BiggestFileTypeContributor(
          statistics.fileSetName,
          fileTypeStats.numberOfFiles,
          fileTypeStats.totalBytes,
          fileTypeStats.indexingTime.sumTime
        )
      )
    }

    for ((indexId, stats) in statistics.statsPerIndexer) {
      val totalStats = totalStatsPerIndexer.getOrPut(indexId) {
        StatsPerIndexer(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexingTimeInAllThreads = 0,
          snapshotInputMappingStats = StatsPerIndexer.SnapshotInputMappingStats(
            requests = 0,
            misses = 0
          )
        )
      }
      totalStats.totalNumberOfFiles += stats.numberOfFiles
      totalStats.totalNumberOfFilesIndexedByExtensions += stats.numberOfFilesIndexedByExtensions
      totalStats.totalBytes += stats.totalBytes
      totalStats.totalIndexingTimeInAllThreads += stats.indexingTime.sumTime
    }
  }

  fun addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics: List<SnapshotInputMappingsStatistics>) {
    for (mappingsStatistic in snapshotInputMappingsStatistics) {
      val totalStats = totalStatsPerIndexer.getOrPut(mappingsStatistic.indexId.name) {
        StatsPerIndexer(
          totalNumberOfFiles = 0,
          totalNumberOfFilesIndexedByExtensions = 0,
          totalBytes = 0,
          totalIndexingTimeInAllThreads = 0,
          snapshotInputMappingStats = StatsPerIndexer.SnapshotInputMappingStats(requests = 0, misses = 0))
      }
      totalStats.snapshotInputMappingStats.requests += mappingsStatistic.totalRequests
      totalStats.snapshotInputMappingStats.misses += mappingsStatistic.totalMisses
    }
  }

  data class StatsPerFileType(
    var totalNumberOfFiles: Int,
    var totalBytes: BytesNumber,
    var totalIndexingTimeInAllThreads: TimeNano,
    var totalContentLoadingTimeInAllThreads: TimeNano,
    val biggestFileTypeContributors: LimitedPriorityQueue<BiggestFileTypeContributor>
  )

  data class BiggestFileTypeContributor(
    val providerName: String,
    val numberOfFiles: Int,
    val totalBytes: BytesNumber,
    val indexingTimeInAllThreads: TimeNano
  )

  data class StatsPerIndexer(
    var totalNumberOfFiles: Int,
    var totalNumberOfFilesIndexedByExtensions: Int,
    var totalBytes: BytesNumber,
    var totalIndexingTimeInAllThreads: TimeNano,
    var snapshotInputMappingStats: SnapshotInputMappingStats
  ) {
    data class SnapshotInputMappingStats(var requests: Long, var misses: Long) {
      val hits: Long get() = requests - misses
    }
  }

  data class IndexingTimes(
    val updatingStart: ZonedDateTime,
    var totalUpdatingTime: TimeNano,
    var updatingEnd: ZonedDateTime = updatingStart,
    var indexingDuration: Duration = Duration.ZERO,
    var pushPropertiesDuration: Duration = Duration.ZERO,
    var indexExtensionsDuration: Duration = Duration.ZERO,
    var scanFilesDuration: Duration = Duration.ZERO,
    var suspendedDuration: Duration = Duration.ZERO,
    var wasInterrupted: Boolean = false
  )
}