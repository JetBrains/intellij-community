// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.util.indexing.diagnostic.*
import java.time.Duration

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000

// Int value that is greater than zero.
// Can be used to skip int value from JSON if it is equal to 0 (to not pollute the JSON report).
typealias PositiveInt = Int?

fun ScanningStatistics.toJsonStatistics() =
  JsonScanningStatistics(
    providerName = fileSetName,
    numberOfScannedFiles = numberOfScannedFiles,
    numberOfFilesForIndexing = numberOfFilesForIndexing,
    numberOfSkippedFiles = numberOfSkippedFiles,
    numberOfUpToDateFiles = numberOfScannedFiles - numberOfFilesForIndexing,
    numberOfFilesFullyIndexedByInfrastructureExtensions = numberOfFilesFullyIndexedByInfrastructureExtension,
    scanningTime = JsonDuration(scanningTime),
    timeProcessingUpToDateFiles = JsonDuration(timeProcessingUpToDateFiles),
    timeUpdatingContentLessIndexes = JsonDuration(timeUpdatingContentLessIndexes),
    timeIndexingWithoutContent = JsonDuration(timeIndexingWithoutContent)
  )

fun IndexingJobStatistics.toJsonStatistics(): JsonFileProviderIndexStatistics {
  val indexedFilePaths = if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) indexedFiles else null

  return JsonFileProviderIndexStatistics(
    providerName = fileSetName,
    totalNumberOfFiles = numberOfIndexedFiles,
    totalNumberOfFilesFullyIndexedByExtensions = numberOfFilesFullyIndexedByExtensions,
    totalIndexingTime = JsonDuration(totalIndexingTime),
    numberOfTooLargeForIndexingFiles = numberOfTooLargeForIndexingFiles,
    indexedFiles = indexedFilePaths
  )
}

fun ProjectIndexingHistory.IndexingTimes.toJson() =
  JsonProjectIndexingHistoryTimes(
    totalUpdatingTime = JsonDuration(Duration.between(updatingStart, updatingEnd).toNanos()),
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
    numberOfFileProviders = scanningStatistics.size,
    totalNumberOfFiles = scanningStatistics.map { it.numberOfScannedFiles }.sum(),
    totalNumberOfUpToDateFiles = scanningStatistics.map { it.numberOfUpToDateFiles }.sum(),
    totalNumberOfFilesIndexedByInfrastructureExtensions = providerStatistics.map { it.totalNumberOfFilesFullyIndexedByExtensions }.sum() +
                                                          scanningStatistics.map { it.numberOfFilesFullyIndexedByInfrastructureExtensions }.sum(),
    times = times.toJson(),
    totalNumberOfTooLargeForIndexingFiles = totalNumberOfTooLargeFiles,
    totalStatsPerFileType = aggregateStatsPerFileType().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    scanningStatistics = scanningStatistics.sortedByDescending { it.scanningTime.nano },
    fileProviderStatistics = providerStatistics.sortedByDescending { it.totalIndexingTime.nano }
  )

private fun ProjectIndexingHistory.aggregateStatsPerFileType(): List<JsonProjectIndexingHistory.JsonStatsPerFileType> {
  val totalIndexingTime = totalStatsPerFileType.values.map { it.totalIndexingTimeInAllThreads }.sum()
  val fileTypeToIndexingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  @Suppress("DuplicatedCode")
  val totalContentLoadingTime = totalStatsPerFileType.values.map { it.totalContentLoadingTimeInAllThreads }.sum()
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

private fun TooLargeForIndexingFile.toJson() = JsonTooLargeForIndexingFile(fileName, JsonFileSize(fileSize))

private fun ProjectIndexingHistory.aggregateStatsPerIndexer(): List<JsonProjectIndexingHistory.JsonStatsPerIndexer> {
  val totalIndexingTime = totalStatsPerIndexer.values.map { it.totalIndexingTimeInAllThreads }.sum()
  val indexIdToIndexingTimePart = totalStatsPerIndexer.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  val indexIdToProcessingSpeed = totalStatsPerIndexer.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexingTimeInAllThreads)
  }

  return totalStatsPerIndexer.map { (indexId, stats) ->
    JsonProjectIndexingHistory.JsonStatsPerIndexer(
      indexId,
      indexIdToIndexingTimePart.getValue(indexId),
      stats.totalNumberOfFiles,
      stats.totalNumberOfFilesIndexedByExtensions,
      JsonFileSize(stats.totalBytes),
      indexIdToProcessingSpeed.getValue(indexId)
    )
  }
}