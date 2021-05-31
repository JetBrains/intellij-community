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
    totalIndexingTime = JsonDuration(indexingVisibleTime),
    contentLoadingTime = JsonDuration(contentLoadingVisibleTime),
    numberOfTooLargeForIndexingFiles = numberOfTooLargeForIndexingFiles,
    slowIndexedFiles = slowIndexedFiles.biggestElements.map { it.toJson() },
    indexedFiles = jsonIndexedFiles
  )
}

fun SlowIndexedFile.toJson() = JsonFileProviderIndexStatistics.JsonSlowIndexedFile(
  fileName = fileName,
  processingTime = JsonDuration(processingTime),
  indexingTime = JsonDuration(indexingTime),
  contentLoadingTime = JsonDuration(contentLoadingTime)
)

fun IndexingJobStatistics.IndexedFile.toJson() = JsonFileProviderIndexStatistics.JsonIndexedFile(
  path = portableFilePath,
  wasFullyIndexedByExtensions = wasFullyIndexedByExtensions
)

fun ProjectIndexingHistory.IndexingTimes.toJson() =
  JsonProjectIndexingHistoryTimes(
    indexingReason = indexingReason,
    totalUpdatingTime = JsonDuration(totalUpdatingTime),
    indexingTime = JsonDuration(indexingDuration.toNanos()),
    contentLoadingTime = JsonDuration(contentLoadingDuration.toNanos()),
    scanFilesTime = JsonDuration(scanFilesDuration.toNanos()),
    pushPropertiesTime = JsonDuration(pushPropertiesDuration.toNanos()),
    indexExtensionsTime = JsonDuration(indexExtensionsDuration.toNanos()),
    updatingStart = JsonDateTime(updatingStart),
    updatingEnd = JsonDateTime(updatingEnd),
    totalSuspendedTime = JsonDuration(suspendedDuration.toNanos()),
    wasInterrupted = wasInterrupted
  )

private fun calculatePercentages(part: Long, total: Long): JsonPercentages = JsonPercentages(part, total)

fun ProjectIndexingHistory.toJson(): JsonProjectIndexingHistory {
  times.contentLoadingDuration = Duration.ofNanos(providerStatistics.sumOf { it.contentLoadingTime.nano })
  return JsonProjectIndexingHistory(
    projectName = project.name,
    times = times.toJson(),
    fileCount = getFileCount(),
    totalStatsPerFileType = aggregateStatsPerFileType().sortedByDescending { it.partOfTotalProcessingTime.doublePercentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    scanningStatistics = scanningStatistics.sortedByDescending { it.scanningTime.nano },
    fileProviderStatistics = providerStatistics.sortedByDescending { it.totalIndexingTime.nano }
  )
}

private fun ProjectIndexingHistory.getFileCount() = JsonProjectIndexingFileCount(
  numberOfFileProviders = scanningStatistics.size,
  numberOfScannedFiles = scanningStatistics.sumBy { it.numberOfScannedFiles },
  numberOfFilesIndexedByInfrastructureExtensionsDuringScan = scanningStatistics.sumOf { it.numberOfFilesFullyIndexedByInfrastructureExtensions },
  numberOfFilesScheduledForIndexingAfterScan = scanningStatistics.sumBy { it.numberOfFilesForIndexing },
  numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage = providerStatistics.sumOf { it.totalNumberOfFilesFullyIndexedByExtensions },
  numberOfFilesIndexedWithLoadingContent = providerStatistics.sumOf { it.totalNumberOfIndexedFiles }
)

private fun ProjectIndexingHistory.aggregateStatsPerFileType(): List<JsonProjectIndexingHistory.JsonStatsPerFileType> {
  val totalProcessingTime = totalStatsPerFileType.values.sumOf { it.totalProcessingTimeInAllThreads }
  val fileTypeToProcessingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalProcessingTimeInAllThreads, totalProcessingTime)
  }

  @Suppress("DuplicatedCode")
  val totalContentLoadingTime = totalStatsPerFileType.values.sumOf { it.totalContentLoadingTimeInAllThreads }
  val fileTypeToContentLoadingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalContentLoadingTimeInAllThreads, totalContentLoadingTime)
  }

  val fileTypeToProcessingSpeed = totalStatsPerFileType.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalProcessingTimeInAllThreads)
  }

  return totalStatsPerFileType.map { (fileType, stats) ->
    val jsonBiggestFileTypeContributors = stats.biggestFileTypeContributors.biggestElements.map {
      JsonProjectIndexingHistory.JsonStatsPerFileType.JsonBiggestFileTypeContributor(
        it.providerName,
        it.numberOfFiles,
        JsonFileSize(it.totalBytes),
        calculatePercentages(it.processingTimeInAllThreads, stats.totalProcessingTimeInAllThreads)
      )
    }
    JsonProjectIndexingHistory.JsonStatsPerFileType(
      fileType,
      fileTypeToProcessingTimePart.getValue(fileType),
      fileTypeToContentLoadingTimePart.getValue(fileType),
      stats.totalNumberOfFiles,
      JsonFileSize(stats.totalBytes),
      fileTypeToProcessingSpeed.getValue(fileType),
      jsonBiggestFileTypeContributors.sortedByDescending { it.partOfTotalProcessingTimeOfThisFileType.doublePercentages }
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