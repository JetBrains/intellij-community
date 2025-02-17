// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.util.concurrent.TimeUnit

/*
 * metricNumberOfIndexedFilesWritingIndexValue <= metricNumberOfIndexedFiles
 *
 * A file sent to indexing is considered indexed;
 * When a new value of some index of a file is written, or outdated value deleted,
 * the file adds to the metricNumberOfIndexedFilesWritingIndexValue
 */
data class IndexingMetrics(val jsonIndexDiagnostics: List<JsonIndexingActivityDiagnostic>) {
  val scanningHistories: List<JsonProjectScanningHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
      .sortedBy { it.times.updatingStart.instant }
  val indexingHistories: List<JsonProjectDumbIndexingHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectDumbIndexingHistory>()
      .sortedBy { it.times.updatingStart.instant }
  private val scanningStatistics: List<JsonScanningStatistics>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
      .flatMap { history -> history.scanningStatistics }

  val totalNumberOfRunsOfScanning: Int
    get() = scanningHistories.count { it.projectName.isNotEmpty() }

  val totalNumberOfRunsOfIndexing: Int
    get() = indexingHistories.count { it.projectName.isNotEmpty() }

  val totalDumbModeTimeWithPauses: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.dumbWallTimeWithPauses.nano)
    }

  val totalTimeOfScanningOrIndexing: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.totalWallTimeWithPauses.nano)
    }

  val totalIndexingTimeWithoutPauses: Long
    get() = TimeUnit.NANOSECONDS.toMillis(indexingHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

  val totalScanFilesTimeWithoutPauses: Long
    get() = TimeUnit.NANOSECONDS.toMillis(scanningHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

  val totalPausedTime: Long
    get() = TimeUnit.NANOSECONDS.toMillis(jsonIndexDiagnostics.sumOf { it.projectIndexingActivityHistory.times.wallTimeOnPause.nano })

  val totalNumberOfIndexedFiles: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles } }

  val totalNumberOfIndexedFilesWritingIndexValues: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles - it.totalNumberOfNothingToWriteFiles } }

  val totalNumberOfIndexedFilesWithNothingToWrite: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfNothingToWriteFiles } }

  val indexedFiles: List<JsonFileProviderIndexStatistics.JsonIndexedFile>
    get() = indexingHistories.flatMap { history -> history.fileProviderStatistics.flatMap { it.indexedFiles ?: emptyList() } }

  val totalNumberOfScannedFiles: Int
    get() = scanningStatistics.sumOf { it.numberOfScannedFiles }

  val totalNumberOfFilesFullyIndexedByExtensions: Int
    get() = jsonIndexDiagnostics.sumOf {
      when (val fileCount = it.projectIndexingActivityHistory.fileCount) {
        is JsonProjectScanningFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan
        is JsonProjectDumbIndexingFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage
      }
    }

  val listOfFilesFullyIndexedByExtensions: List<String>
    get() {
      val indexedFiles = mutableListOf<String>()
      jsonIndexDiagnostics.forEach { diagnostic ->
        when (val history = diagnostic.projectIndexingActivityHistory) {
          is JsonProjectScanningHistory -> history.scanningStatistics.forEach {
            indexedFiles.addAll(it.filesFullyIndexedByInfrastructureExtensions)
          }
          is JsonProjectDumbIndexingHistory -> {
            history.fileProviderStatistics.forEach {
              indexedFiles.addAll(it.filesFullyIndexedByExtensions)
            }
          }
        }
      }
      return indexedFiles.distinct()
    }

  val numberOfIndexedByExtensionsFilesForEachProvider: Map<String, Int>
    get() {
      val indexedByExtensionsFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by extensions */>()
      scanningStatistics.forEach { stat ->
        indexedByExtensionsFiles[stat.providerName] = indexedByExtensionsFiles.getOrDefault(stat.providerName, 0) +
                                                      stat.numberOfFilesFullyIndexedByInfrastructureExtensions
      }
      return indexedByExtensionsFiles
    }

  val numberOfIndexedFilesByUsualIndexesPerProvider: Map<String, Int>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by usual indexes */>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { indexStats ->
        indexedFiles[indexStats.providerName] = indexedFiles.getOrDefault(indexStats.providerName,
                                                                          0) + indexStats.totalNumberOfIndexedFiles
      }
      return indexedFiles
    }

  val scanningStatisticsByProviders: Map<String, AggregatedScanningStatistics>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, AggregatedScanningStatistics>()
      scanningStatistics.forEach { stats ->
        val value: AggregatedScanningStatistics = indexedFiles.getOrDefault(stats.providerName, AggregatedScanningStatistics())
        indexedFiles[stats.providerName] = value.merge(stats)
      }
      return indexedFiles
    }

  val numberOfFullRescanning: Int
    get() = scanningHistories.count { it.times.scanningType.isFull }

  val allIndexedFiles: Map<String, List<PortableFilePath>> //without shared indexes
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, MutableList<PortableFilePath>>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { fileProviderStatistic ->
        indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } +=
          fileProviderStatistic.indexedFiles.orEmpty().map { it.path }
      }
      return indexedFiles
    }

  val processingSpeedPerFileTypeWorst: Map<String, Int>
    get() {
      return indexingHistories.flatMap { it.totalStatsPerFileType }.groupBy { it.fileType }.mapValues {
        it.value.minOf { jsonStatsPerFileType -> jsonStatsPerFileType.totalProcessingSpeed.toKiloBytesPerSecond() }
      }
    }

  val processingSpeedPerFileTypeAvg: Map<String, Int>
    get() {
      return indexingHistories.flatMap { history ->
        history.totalStatsPerFileType.map {
          Triple(it.fileType, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
        }
      }.computeAverageSpeed()
    }

  private fun Collection<Triple<String, Double, JsonFileSize>>.computeAverageSpeed(): Map<String, Int> = groupBy { it.first }.mapValues { entry ->
    JsonProcessingSpeed(entry.value.sumOf { it.third.bytes }, entry.value.sumOf { it.second.toLong() }).toKiloBytesPerSecond()
  }

  val processingSpeedPerBaseLanguageWorst: Map<String, Int>
    get() {
      return indexingHistories.flatMap { it.totalStatsPerBaseLanguage }.groupBy { it.language }.mapValues {
        it.value.minOf { jsonStatsPerParentLanguage -> jsonStatsPerParentLanguage.totalProcessingSpeed.toKiloBytesPerSecond() }
      }
    }

  val processingSpeedPerBaseLanguageAvg: Map<String, Int>
    get() {
      return indexingHistories.flatMap { history ->
        history.totalStatsPerBaseLanguage.map {
          Triple(it.language, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
        }
      }.computeAverageSpeed()
    }

  val processingTimePerFileType: Map<String, Long>
    get() {
      val indexingDurationMap = mutableMapOf<String, Long>()
      indexingHistories.forEach { indexingHistory ->
        indexingHistory.totalStatsPerFileType.forEach { totalStatsPerFileType ->
          val duration = (indexingHistory.times.totalWallTimeWithPauses.nano * totalStatsPerFileType.partOfTotalProcessingTime.partition).toLong()
          indexingDurationMap[totalStatsPerFileType.fileType] = indexingDurationMap[totalStatsPerFileType.fileType]?.let { it + duration }
                                                                ?: duration
        }
      }
      return indexingDurationMap
    }

  val slowIndexedFiles: Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>
    get() {
      val indexedFiles = hashMapOf<String, MutableList<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { fileProviderStatistic ->
        indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } += fileProviderStatistic.slowIndexedFiles
      }
      return indexedFiles
    }

  fun toReportTimeAttributes(): Map<String, String> = mapOf(
    "suspended time" to StringUtil.formatDuration(totalPausedTime),
    "total scan files time" to StringUtil.formatDuration(totalScanFilesTimeWithoutPauses),
    "total indexing time" to StringUtil.formatDuration(totalIndexingTimeWithoutPauses),
    "total updating time" to StringUtil.formatDuration(totalTimeOfScanningOrIndexing),
  )

  fun toReportCountersAttributes(): Map<String, String> = mapOf(
    "number of indexed files" to totalNumberOfIndexedFiles.toString(),
    "number of scanned files" to totalNumberOfScannedFiles.toString(),
    "number of files indexed by extensions" to totalNumberOfFilesFullyIndexedByExtensions.toString(),
    "number of scanning runs" to totalNumberOfRunsOfScanning.toString(),
    "number of indexing runs" to totalNumberOfRunsOfIndexing.toString(),
    "number of full rescannings" to numberOfFullRescanning.toString()
  )
}

data class AggregatedScanningStatistics(val numberOfScannedFiles: Int = 0, val numberOfSkippedFiles: Int = 0, val totalSumOfThreadTimesWithPauses: Int = 0) {
  fun merge(scanningStatistics: JsonScanningStatistics): AggregatedScanningStatistics {
    return AggregatedScanningStatistics(
      numberOfScannedFiles = numberOfScannedFiles + scanningStatistics.numberOfScannedFiles,
      numberOfSkippedFiles = numberOfSkippedFiles + scanningStatistics.numberOfSkippedFiles,
      totalSumOfThreadTimesWithPauses = totalSumOfThreadTimesWithPauses + scanningStatistics.totalOneThreadTimeWithPauses.milliseconds.toInt()
    )
  }
}

sealed class IndexingMetric(val name: String) {
  class Duration(name: String, val durationMillis: Int) : IndexingMetric(name)
  class Counter(name: String, val value: Int) : IndexingMetric(name)
}


fun IndexingMetrics.getListOfIndexingMetrics(): List<IndexingMetric> {
  val numberOfIndexedFiles = totalNumberOfIndexedFiles
  val numberOfFilesFullyIndexedByExtensions = totalNumberOfFilesFullyIndexedByExtensions
  return listOf(
    IndexingMetric.Duration("indexingTimeWithoutPauses", durationMillis = totalIndexingTimeWithoutPauses.toInt()),
    IndexingMetric.Duration("scanningTimeWithoutPauses", durationMillis = totalScanFilesTimeWithoutPauses.toInt()),
    IndexingMetric.Duration("pausedTimeInIndexingOrScanning", durationMillis = totalPausedTime.toInt()),
    IndexingMetric.Duration("dumbModeTimeWithPauses", durationMillis = totalDumbModeTimeWithPauses.toInt()),
    IndexingMetric.Counter("numberOfIndexedFiles", value = numberOfIndexedFiles),
    IndexingMetric.Counter("numberOfIndexedFilesWritingIndexValue", value = totalNumberOfIndexedFilesWritingIndexValues),
    IndexingMetric.Counter("numberOfIndexedFilesWithNothingToWrite", value = totalNumberOfIndexedFilesWithNothingToWrite),
    IndexingMetric.Counter("numberOfFilesIndexedByExtensions", value = numberOfFilesFullyIndexedByExtensions),
    IndexingMetric.Counter("numberOfFilesIndexedWithoutExtensions",
                                  value = (numberOfIndexedFiles - numberOfFilesFullyIndexedByExtensions)),
    IndexingMetric.Counter("numberOfRunsOfScannning", value = totalNumberOfRunsOfScanning),
    IndexingMetric.Counter("numberOfRunsOfIndexing", value = totalNumberOfRunsOfIndexing)
  ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeAvg, "Avg") +
         getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeWorst, "Worst") +
         getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageAvg, "Avg") +
         getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageWorst, "Worst") +
         getProcessingTimeOfFileType(processingTimePerFileType)
}


private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>, suffix: String): List<IndexingMetric> =
  mapFileTypeToSpeed.map {
    IndexingMetric.Counter("processingSpeed$suffix#${it.key}", value = it.value)
  }

private fun getProcessingSpeedOfBaseLanguages(mapBaseLanguageToSpeed: Map<String, Int>, suffix: String): List<IndexingMetric> =
  mapBaseLanguageToSpeed.map {
    IndexingMetric.Counter("processingSpeedOfBaseLanguage$suffix#${it.key}", value = it.value)
  }

private fun getProcessingTimeOfFileType(mapFileTypeToDuration: Map<String, Long>): List<IndexingMetric> =
  mapFileTypeToDuration.map {
    IndexingMetric.Duration("processingTime#${it.key}", durationMillis = TimeUnit.NANOSECONDS.toMillis(it.value.toLong()).toInt())
  }
