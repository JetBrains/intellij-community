// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.util.indexing.diagnostic.*
import java.time.Duration
import java.time.Instant

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000

// Int value that is greater than zero.
// Can be used to skip int value from JSON if it is equal to 0 (to not pollute the JSON report).
typealias PositiveInt = Int?

fun Int.toPositiveInt() = takeIf { it > 0 }

fun ScanningStatistics.toJsonStatistics() =
  JsonScanningStatistics(
    providerName = fileSetName,
    numberOfScannedFiles = numberOfScannedFiles,
    numberOfUpToDateFiles = numberOfScannedFiles - numberOfFilesForIndexing,
    numberOfFilesFullyIndexedByInfrastructureExtensions = numberOfFilesFullyIndexedByInfrastructureExtension,
    scanningTime = JsonDuration(scanningTime),
    timeProcessingUpToDateFiles = JsonDuration(timeProcessingUpToDateFiles),
    timeUpdatingContentLessIndexes = JsonDuration(timeUpdatingContentLessIndexes),
    timeIndexingWithoutContent = JsonDuration(timeIndexingWithoutContent)
  )

fun IndexingJobStatistics.toJsonStatistics(): JsonFileProviderIndexStatistics {
  val statsPerFileType = aggregateStatsPerFileType()
  val allStatsPerIndexer = aggregateStatsPerIndexer()
  val (statsPerIndexer, fastIndexers) = allStatsPerIndexer.partition { it.partOfTotalIndexingTime.percentages > 0.01 }
  val indexedFilePaths = if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) indexedFiles else null

  return JsonFileProviderIndexStatistics(
    providerName = fileSetName,
    totalNumberOfFiles = numberOfIndexedFiles,
    totalNumberOfFilesFullyIndexedByExtensions = numberOfFilesFullyIndexedByExtensions,
    totalIndexingTime = JsonDuration(totalIndexingTime),
    numberOfTooLargeForIndexingFiles = numberOfTooLargeForIndexingFiles.toPositiveInt(),
    tooLargeForIndexingFiles = tooLargeForIndexingFiles.biggestElements.map { it.toJson() }.takeIf { it.isNotEmpty() },
    statsPerFileType = statsPerFileType.sortedByDescending { it.partOfTotalIndexingTime.percentages },
    statsPerIndexer = statsPerIndexer.sortedByDescending { it.partOfTotalIndexingTime.percentages },
    fastIndexers = fastIndexers.map { it.indexId }.sorted(),
    indexedFiles = indexedFilePaths
  )
}

private fun IndexingJobStatistics.aggregateStatsPerFileType(): List<JsonFileProviderIndexStatistics.JsonStatsPerFileType> {
  val totalIndexingTimePerFileType = statsPerFileType.values
    .filterNot { it.indexingTime.isEmpty }
    .map { it.indexingTime.sumTime }
    .sum()

  val totalContentLoadingTimePerFileType = statsPerFileType.values
    .filterNot { it.contentLoadingTime.isEmpty }
    .map { it.contentLoadingTime.sumTime }
    .sum()

  return statsPerFileType
    .mapNotNull { (fileTypeName, stats) ->
      JsonFileProviderIndexStatistics.JsonStatsPerFileType(
        fileTypeName,
        stats.numberOfFiles,
        JsonFileSize(stats.totalBytes),
        calculatePercentages(stats.indexingTime.sumTime, totalIndexingTimePerFileType),
        calculatePercentages(stats.contentLoadingTime.sumTime, totalContentLoadingTimePerFileType)
      )
    }
}

private fun IndexingJobStatistics.aggregateStatsPerIndexer(): List<JsonFileProviderIndexStatistics.JsonStatsPerIndexer> {
  val totalIndexingTimePerIndexer = statsPerIndexer.values
    .filterNot { it.indexingTime.isEmpty }
    .map { it.indexingTime.sumTime }
    .sum()

  return statsPerIndexer
    .mapNotNull { (indexId, stats) ->
      JsonFileProviderIndexStatistics.JsonStatsPerIndexer(
        indexId,
        stats.numberOfFiles,
        stats.numberOfFilesIndexedByExtensions,
        calculatePercentages(stats.indexingTime.sumTime, totalIndexingTimePerIndexer)
      )
    }
}

fun ProjectIndexingHistory.IndexingTimes.toJson() =
  JsonProjectIndexingHistoryTimes(
    totalUpdatingTime = nullableJsonDuration(totalStart, totalEnd),
    indexingTime = nullableJsonDuration(indexingStart, indexingEnd),
    scanFilesTime = nullableJsonDuration(scanFilesStart, scanFilesEnd),
    pushPropertiesTime = nullableJsonDuration(pushPropertiesStart, pushPropertiesEnd),
    indexExtensionsTime = nullableJsonDuration(indexExtensionsStart, indexExtensionsEnd),
    updatingStart = JsonDateTime(totalStart!!),
    updatingEnd = JsonDateTime(totalEnd!!),
    totalSuspendedTime = suspendedDuration?.let { JsonDuration(it.toNanos()) },
    wasInterrupted = wasInterrupted
  )

private fun nullableJsonDuration(from: Instant?, to: Instant?): JsonDuration? {
  if (from != null && to != null) {
    return JsonDuration(Duration.between(from, to).toNanos())
  }
  return null
}

private fun calculatePercentages(part: Long, total: Long): JsonPercentages =
  if (total == 0L) {
    JsonPercentages(1.0)
  }
  else {
    JsonPercentages(part.toDouble() / total)
  }

fun ProjectIndexingHistory.toJson(): JsonProjectIndexingHistory =
  JsonProjectIndexingHistory(
    projectName = project.name,
    numberOfFileProviders = scanningStatistics.size,
    totalNumberOfFiles = scanningStatistics.map { it.numberOfScannedFiles }.sum(),
    totalNumberOfUpToDateFiles = scanningStatistics.map { it.numberOfUpToDateFiles }.sum(),
    times = times.toJson(),
    numberOfIndexingThreads = numberOfIndexingThreads,
    totalNumberOfTooLargeForIndexingFiles = totalNumberOfTooLargeFiles.toPositiveInt(),
    tooLargeForIndexingFiles = totalTooLargeFiles.biggestElements.map { it.toJson() }.takeIf { it.isNotEmpty() },
    totalStatsPerFileType = aggregateStatsPerFileType().sortedByDescending { it.partOfTotalIndexingTime.percentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.percentages },
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
      jsonBiggestFileTypeContributors.sortedByDescending { it.partOfTotalIndexingTimeOfThisFileType.percentages }
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