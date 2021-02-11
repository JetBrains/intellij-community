// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.util.indexing.diagnostic.*
import java.time.Duration

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000

// Int value that is greater than zero.
// Can be used to skip int value from JSON if it is equal to 0 (to not pollute the JSON report).
typealias PositiveInt = Int?

fun ScanningStatistics.toJsonStatistics(): JsonScanningStatistics {
  val jsonScannedFiles = if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
    scannedFiles.map { it.toJson() }
  }
  else {
    null
  }

  return JsonScanningStatistics(
    providerName = fileSetName,
    numberOfScannedFiles = numberOfScannedFiles,
    numberOfFilesForIndexing = numberOfFilesForIndexing,
    numberOfSkippedFiles = numberOfSkippedFiles,
    numberOfFilesFullyIndexedByInfrastructureExtensions = numberOfFilesFullyIndexedByInfrastructureExtension,
    scanningTime = JsonDuration(scanningTime),
    timeProcessingUpToDateFiles = JsonDuration(timeProcessingUpToDateFiles),
    timeUpdatingContentLessIndexes = JsonDuration(timeUpdatingContentLessIndexes),
    timeIndexingWithoutContent = JsonDuration(timeIndexingWithoutContent),
    scannedFiles = jsonScannedFiles
  )
}

fun ScanningStatistics.ScannedFile.toJson(): JsonScanningStatistics.JsonScannedFile =
  JsonScanningStatistics.JsonScannedFile(
    path = portableFilePath,
    isUpToDate = isUpToDate,
    wasFullyIndexedByInfrastructureExtension = wasFullyIndexedByInfrastructureExtension
  )

@Suppress("DuplicatedCode")
fun IndexingJobStatistics.toJsonStatistics(): JsonFileProviderIndexStatistics {
  val jsonIndexedFiles = if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
    indexedFiles.map { it.toJson() }
  }
  else {
    null
  }

  return JsonFileProviderIndexStatistics(
    providerName = fileSetName,
    totalNumberOfIndexedFiles = numberOfIndexedFiles,
    totalNumberOfFilesFullyIndexedByExtensions = numberOfFilesFullyIndexedByExtensions,
    totalIndexingTime = JsonDuration(totalIndexingTime),
    numberOfTooLargeForIndexingFiles = numberOfTooLargeForIndexingFiles,
    indexedFiles = jsonIndexedFiles
  )
}

fun IndexingJobStatistics.IndexedFile.toJson() = JsonFileProviderIndexStatistics.JsonIndexedFile(
  path = portableFilePath,
  wasFullyIndexedByExtensions = wasFullyIndexedByExtensions
)

fun ProjectIndexingHistory.IndexingTimes.toJson() =
  JsonProjectIndexingHistoryTimes(
    totalUpdatingTime = JsonDuration(totalUpdatingTime),
    indexingTime = JsonDuration(indexingDuration.toNanos()),
    scanFilesTime = JsonDuration(scanFilesDuration.toNanos()),
    pushPropertiesTime = JsonDuration(pushPropertiesDuration.toNanos()),
    indexExtensionsTime = JsonDuration(indexExtensionsDuration.toNanos()),
    updatingStart = JsonDateTime(updatingStart),
    updatingEnd = JsonDateTime(updatingEnd),
    totalSuspendedTime = JsonDuration(suspendedDuration.toNanos()),
    wasInterrupted = wasInterrupted
  )

private fun calculatePercentages(part: Long, total: Long): JsonPercentages = JsonPercentages(part, total)

fun ProjectIndexingHistory.toJson(): JsonProjectIndexingHistory =
  JsonProjectIndexingHistory(
    projectName = project.name,
    times = times.toJson(),
    totalStatsPerFileType = aggregateStatsPerFileType().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    scanningStatistics = scanningStatistics.sortedByDescending { it.scanningTime.nano },
    fileProviderStatistics = providerStatistics.sortedByDescending { it.totalIndexingTime.nano }
  )

private fun ProjectIndexingHistory.aggregateStatsPerFileType(): List<JsonProjectIndexingHistory.JsonStatsPerFileType> {
  val totalIndexingTime = totalStatsPerFileType.values.sumOf { it.totalIndexingTimeInAllThreads }
  val fileTypeToIndexingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  @Suppress("DuplicatedCode")
  val totalContentLoadingTime = totalStatsPerFileType.values.sumOf { it.totalContentLoadingTimeInAllThreads }
  val fileTypeToContentLoadingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalContentLoadingTimeInAllThreads, totalContentLoadingTime)
  }

  val fileTypeToProcessingSpeed = totalStatsPerFileType.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexingTimeInAllThreads)
  }

  return totalStatsPerFileType.map { (fileType, stats) ->
    val jsonBiggestFileTypeContributors = stats.biggestFileTypeContributors.biggestElements.map {
      JsonProjectIndexingHistory.JsonStatsPerFileType.JsonBiggestFileTypeContributor(
        it.providerName,
        it.numberOfFiles,
        JsonFileSize(it.totalBytes),
        calculatePercentages(it.indexingTimeInAllThreads, stats.totalIndexingTimeInAllThreads)
      )
    }
    JsonProjectIndexingHistory.JsonStatsPerFileType(
      fileType,
      fileTypeToIndexingTimePart.getValue(fileType),
      fileTypeToContentLoadingTimePart.getValue(fileType),
      stats.totalNumberOfFiles,
      JsonFileSize(stats.totalBytes),
      fileTypeToProcessingSpeed.getValue(fileType),
      jsonBiggestFileTypeContributors.sortedByDescending { it.partOfTotalIndexingTimeOfThisFileType.doublePercentages }
    )
  }
}

private fun ProjectIndexingHistory.aggregateStatsPerIndexer(): List<JsonProjectIndexingHistory.JsonStatsPerIndexer> {
  val totalIndexingTime = totalStatsPerIndexer.values.sumOf { it.totalIndexingTimeInAllThreads }
  val indexIdToIndexingTimePart = totalStatsPerIndexer.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  val indexIdToProcessingSpeed = totalStatsPerIndexer.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexingTimeInAllThreads)
  }

  return totalStatsPerIndexer.map { (indexId, stats) ->
    JsonProjectIndexingHistory.JsonStatsPerIndexer(
      indexId = indexId,
      partOfTotalIndexingTime = indexIdToIndexingTimePart.getValue(indexId),
      totalNumberOfFiles = stats.totalNumberOfFiles,
      totalNumberOfFilesIndexedByExtensions = stats.totalNumberOfFilesIndexedByExtensions,
      totalFilesSize = JsonFileSize(stats.totalBytes),
      indexingSpeed = indexIdToProcessingSpeed.getValue(indexId),
      snapshotInputMappingStats = JsonProjectIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats(
        totalRequests = stats.snapshotInputMappingStats.requests,
        totalMisses = stats.snapshotInputMappingStats.misses,
        totalHits = stats.snapshotInputMappingStats.hits
      )
    )
  }
}