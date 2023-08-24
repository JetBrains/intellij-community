// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.util.indexing.diagnostic.*
import com.intellij.util.indexing.diagnostic.dto.JsonProjectDumbIndexingHistory.JsonStatsPerFileType
import com.intellij.util.indexing.diagnostic.dto.JsonProjectDumbIndexingHistory.JsonStatsPerParentLanguage
import java.time.Duration

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000

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
    filesFullyIndexedByInfrastructureExtensions = listOfFilesFullyIndexedByInfrastructureExtension,
    statusTime = JsonDuration(statusTime),
    scanningTime = JsonDuration(scanningTime),
    timeProcessingUpToDateFiles = JsonDuration(timeProcessingUpToDateFiles),
    timeUpdatingContentLessIndexes = JsonDuration(timeUpdatingContentLessIndexes),
    timeIndexingWithoutContentViaInfrastructureExtension = JsonDuration(timeIndexingWithoutContentViaInfrastructureExtension),
    roots = providerRoots,
    scannedFiles = jsonScannedFiles
  )
}

fun ChangedFilesDuringIndexingStatistics.toJsonStatistics(): JsonChangedFilesDuringIndexingStatistics {
  return JsonChangedFilesDuringIndexingStatistics(numberOfFiles, JsonDuration(retrievingTime))
}

fun ScanningStatistics.ScannedFile.toJson(): JsonScanningStatistics.JsonScannedFile =
  JsonScanningStatistics.JsonScannedFile(
    path = portableFilePath,
    isUpToDate = isUpToDate,
    wasFullyIndexedByInfrastructureExtension = wasFullyIndexedByInfrastructureExtension
  )

fun IndexingFileSetStatistics.toJsonStatistics(visibleTimeToAllThreadsTimeRatio: Double): JsonFileProviderIndexStatistics {
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
    filesFullyIndexedByExtensions = listOfFilesFullyIndexedByExtensions,
    totalIndexingVisibleTime = convertAllThreadsTimeToVisibleDuration(processingTimeInAllThreads, visibleTimeToAllThreadsTimeRatio),
    contentLoadingVisibleTime = convertAllThreadsTimeToVisibleDuration(contentLoadingTimeInAllThreads, visibleTimeToAllThreadsTimeRatio),
    numberOfTooLargeForIndexingFiles = numberOfTooLargeForIndexingFiles,
    slowIndexedFiles = slowIndexedFiles.biggestElements.map { it.toJson() },
    isAppliedAllValuesSeparately = allValuesAppliedSeparately,
    separateApplyingIndexesVisibleTime = convertAllThreadsTimeToVisibleDuration(allSeparateApplicationTimeInAllThreads,
                                                                                visibleTimeToAllThreadsTimeRatio),
    indexedFiles = jsonIndexedFiles
  )
}

private fun convertAllThreadsTimeToVisibleDuration(allThreadsTime: TimeNano, visibleTimeToAllThreadsTimeRatio: Double) =
  JsonDuration((allThreadsTime * visibleTimeToAllThreadsTimeRatio).toLong())

fun SlowIndexedFile.toJson(): JsonFileProviderIndexStatistics.JsonSlowIndexedFile = JsonFileProviderIndexStatistics.JsonSlowIndexedFile(
  fileName = fileName,
  processingTime = JsonDuration(processingTime),
  evaluationOfIndexValueChangerTime = JsonDuration(evaluationOfIndexValueChangerTime),
  contentLoadingTime = JsonDuration(contentLoadingTime)
)

fun IndexingFileSetStatistics.IndexedFile.toJson(): JsonFileProviderIndexStatistics.JsonIndexedFile = JsonFileProviderIndexStatistics.JsonIndexedFile(
  path = portableFilePath,
  wasFullyIndexedByExtensions = wasFullyIndexedByExtensions
)

fun IndexingTimes.toJson(): JsonProjectIndexingHistoryTimes =
  JsonProjectIndexingHistoryTimes(
    indexingReason = indexingReason,
    scanningType = scanningType,
    totalUpdatingTime = JsonDuration(totalUpdatingTime),
    indexingTime = JsonDuration(indexingDuration.toNanos()),
    contentLoadingVisibleTime = JsonDuration(contentLoadingVisibleDuration.toNanos()),
    creatingIteratorsTime = JsonDuration(creatingIteratorsDuration.toNanos()),
    scanFilesTime = JsonDuration(scanFilesDuration.toNanos()),
    pushPropertiesTime = JsonDuration(pushPropertiesDuration.toNanos()),
    indexExtensionsTime = JsonDuration(indexExtensionsDuration.toNanos()),
    isAppliedAllValuesSeparately = appliedAllValuesSeparately,
    separateApplyingIndexesVisibleTime = JsonDuration(separateValueApplicationVisibleTime),
    updatingStart = JsonDateTime(updatingStart),
    updatingEnd = JsonDateTime(updatingEnd),
    totalSuspendedTime = JsonDuration(suspendedDuration.toNanos()),
    wasInterrupted = wasInterrupted
  )

fun ScanningTimes.toJson(): JsonProjectScanningHistoryTimes =
  JsonProjectScanningHistoryTimes(
    scanningReason = scanningReason,
    scanningType = scanningType,
    scanningId = scanningId,
    totalWallTimeWithPauses = JsonDuration(totalUpdatingTime),
    creatingIteratorsTime = JsonDuration(creatingIteratorsDuration.toNanos()),
    collectingIndexableFilesTime = JsonDuration(collectingIndexableFilesDuration.toNanos()),
    delayedPushPropertiesStageTime = JsonDuration(delayedPushPropertiesStageDuration.toNanos()),
    indexExtensionsTime = JsonDuration(indexExtensionsDuration.toNanos()),
    updatingStart = JsonDateTime(updatingStart),
    updatingEnd = JsonDateTime(updatingEnd),
    wallTimeOnPause = JsonDuration(pausedDuration.toNanos()),
    wasInterrupted = wasInterrupted,
    dumbModeStart = dumbModeStart?.let { JsonDateTime(it) },
    dumbWallTimeWithoutPauses = JsonDuration(dumbModeWithoutPausesDuration.toNanos()),
    dumbWallTimeWithPauses = JsonDuration(dumbModeWithPausesDuration.toNanos())
  )

fun DumbIndexingTimes.toJson(): JsonProjectDumbIndexingHistoryTimes =
  JsonProjectDumbIndexingHistoryTimes(
    scanningIds = scanningIds.toSortedSet(),
    totalWallTimeWithPauses = JsonDuration(totalUpdatingTime),
    contentLoadingVisibleTime = JsonDuration(contentLoadingVisibleDuration.toNanos()),
    retrievingChangedDuringIndexingFilesTime = JsonDuration(retrievingChangedDuringIndexingFilesDuration.toNanos()),
    isAppliedAllValuesSeparately = appliedAllValuesSeparately,
    separateApplyingIndexesVisibleTime = JsonDuration(separateValueApplicationVisibleTime),
    updatingStart = JsonDateTime(updatingStart),
    updatingEnd = JsonDateTime(updatingEnd),
    wallTimeOnPause = JsonDuration(pausedDuration.toNanos()),
    wasInterrupted = wasInterrupted
  )

private fun calculatePercentages(part: Long, total: Long): JsonPercentages = JsonPercentages(part, total)

fun ProjectIndexingHistoryImpl.toJson(): JsonProjectIndexingHistory {
  val timesImpl = times as ProjectIndexingHistoryImpl.IndexingTimesImpl
  timesImpl.contentLoadingVisibleDuration = Duration.ofNanos(providerStatistics.sumOf { it.contentLoadingVisibleTime.nano })
  if (providerStatistics.all { it.isAppliedAllValuesSeparately }) {
    timesImpl.appliedAllValuesSeparately = true
    timesImpl.separateValueApplicationVisibleTime = providerStatistics.sumOf { it.separateApplyingIndexesVisibleTime.nano }
  }
  else {
    timesImpl.appliedAllValuesSeparately = false
    timesImpl.separateValueApplicationVisibleTime = 0
  }
  return JsonProjectIndexingHistory(
    projectName = project.name,
    times = times.toJson(),
    fileCount = getFileCount(),
    totalStatsPerFileType = aggregateStatsPerFileType().sortedByDescending { it.partOfTotalProcessingTime.doublePercentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    scanningStatistics = scanningStatistics.sortedByDescending { it.scanningTime.nano },
    fileProviderStatistics = providerStatistics.sortedByDescending { it.totalIndexingVisibleTime.nano },
    visibleTimeToAllThreadTimeRatio = visibleTimeToAllThreadsTimeRatio
  )
}

fun ProjectIndexingActivityHistory.toJson(): JsonProjectIndexingActivityHistory =
  when (this) {
    is ProjectScanningHistoryImpl -> changeToJson()
    is ProjectDumbIndexingHistoryImpl -> changeToJson()
    else -> throw IllegalStateException("Unexpected ProjectIndexingActivityHistory ${this}")
  }

private fun ProjectScanningHistoryImpl.changeToJson(): JsonProjectScanningHistory = JsonProjectScanningHistory(
  projectName = project.name,
  times = times.toJson(),
  fileCount = getFileCount(),
  scanningStatistics = scanningStatistics.sortedByDescending { it.scanningTime.nano }
)

private fun ProjectDumbIndexingHistoryImpl.changeToJson(): JsonProjectDumbIndexingHistory {
  val timesImpl = times as ProjectDumbIndexingHistoryImpl.DumbIndexingTimesImpl
  timesImpl.contentLoadingVisibleDuration = Duration.ofNanos(providerStatistics.sumOf { it.contentLoadingVisibleTime.nano })
  if (providerStatistics.all { it.isAppliedAllValuesSeparately }) {
    timesImpl.appliedAllValuesSeparately = true
    timesImpl.separateValueApplicationVisibleTime = providerStatistics.sumOf { it.separateApplyingIndexesVisibleTime.nano }
  }
  else {
    timesImpl.appliedAllValuesSeparately = false
    timesImpl.separateValueApplicationVisibleTime = 0
  }
  val (statsPerFileType, statsPerParentLanguage) = aggregateStatsPerFileTypeAndLanguage()
  return JsonProjectDumbIndexingHistory(
    projectName = project.name,
    times = times.toJson(),
    fileCount = getFileCount(),
    totalStatsPerFileType = statsPerFileType.sortedByDescending { it.partOfTotalProcessingTime.doublePercentages },
    totalStatsPerBaseLanguage = statsPerParentLanguage.sortedByDescending { it.partOfTotalProcessingTime.doublePercentages },
    totalStatsPerIndexer = aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.doublePercentages },
    statisticsOfChangedDuringIndexingFiles = changedDuringIndexingFilesStat,
    fileProviderStatistics = providerStatistics.sortedByDescending { it.totalIndexingVisibleTime.nano },
    visibleTimeToAllThreadTimeRatio = visibleTimeToAllThreadsTimeRatio
  )
}

private fun ProjectIndexingHistoryImpl.getFileCount() = JsonProjectIndexingFileCount(
  numberOfFileProviders = scanningStatistics.size,
  numberOfScannedFiles = scanningStatistics.sumOf { it.numberOfScannedFiles },
  numberOfFilesIndexedByInfrastructureExtensionsDuringScan = scanningStatistics.sumOf { it.numberOfFilesFullyIndexedByInfrastructureExtensions },
  numberOfFilesScheduledForIndexingAfterScan = scanningStatistics.sumOf { it.numberOfFilesForIndexing },
  numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage = providerStatistics.sumOf { it.totalNumberOfFilesFullyIndexedByExtensions },
  numberOfFilesIndexedWithLoadingContent = providerStatistics.sumOf { it.totalNumberOfIndexedFiles }
)

private fun ProjectScanningHistoryImpl.getFileCount() = JsonProjectScanningFileCount(
  numberOfFileProviders = scanningStatistics.size,
  numberOfScannedFiles = scanningStatistics.sumOf { it.numberOfScannedFiles },
  numberOfFilesIndexedByInfrastructureExtensionsDuringScan = scanningStatistics.sumOf { it.numberOfFilesFullyIndexedByInfrastructureExtensions },
  numberOfFilesScheduledForIndexingAfterScan = scanningStatistics.sumOf { it.numberOfFilesForIndexing }
)

private fun ProjectDumbIndexingHistoryImpl.getFileCount() = JsonProjectDumbIndexingFileCount(
  numberOfChangedDuringIndexingFiles = changedDuringIndexingFilesStat.numberOfFiles,
  numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage = providerStatistics.sumOf { it.totalNumberOfFilesFullyIndexedByExtensions },
  numberOfFilesIndexedWithLoadingContent = providerStatistics.sumOf { it.totalNumberOfIndexedFiles }
)

private fun ProjectIndexingHistoryImpl.aggregateStatsPerFileType(): List<JsonProjectIndexingHistory.JsonStatsPerFileType> {
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

private fun ProjectDumbIndexingHistoryImpl.aggregateStatsPerFileTypeAndLanguage(): Pair<List<JsonStatsPerFileType>, List<JsonStatsPerParentLanguage>> {
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

  data class LanguageData(var totalNumberOfFiles: Int,
                          var totalBytes: BytesNumber,
                          var totalProcessingTimeInAllThreads: TimeNano,
                          val totalContentLoadingTime: TimeNano) {
    fun plus(fileTypeStats: ProjectDumbIndexingHistoryImpl.StatsPerFileTypeImpl): LanguageData =
      LanguageData(totalNumberOfFiles = totalNumberOfFiles + fileTypeStats.totalNumberOfFiles,
                   totalBytes = totalBytes + fileTypeStats.totalBytes,
                   totalProcessingTimeInAllThreads = totalProcessingTimeInAllThreads + fileTypeStats.totalProcessingTimeInAllThreads,
                   totalContentLoadingTime = totalContentLoadingTime + fileTypeStats.totalContentLoadingTimeInAllThreads)
  }

  val languageMap: MutableMap<String, LanguageData> = mutableMapOf()
  totalStatsPerFileType.values.forEach { stat ->
    stat.parentLanguages.forEach { lang ->
      val data: LanguageData = languageMap.computeIfAbsent(lang) {
        LanguageData(0, 0, 0, 0)
      }
      languageMap[lang] = data.plus(stat)
    }
  }

  val statsPerFileTypes = totalStatsPerFileType.map { (fileType, stats) ->
    val jsonBiggestFileTypeContributors = stats.biggestFileTypeContributors.biggestElements.map {
      JsonStatsPerFileType.JsonBiggestFileTypeContributor(
        it.providerName,
        it.numberOfFiles,
        JsonFileSize(it.totalBytes),
        calculatePercentages(it.processingTimeInAllThreads, stats.totalProcessingTimeInAllThreads)
      )
    }
    JsonStatsPerFileType(
      fileType,
      fileTypeToProcessingTimePart.getValue(fileType),
      fileTypeToContentLoadingTimePart.getValue(fileType),
      stats.totalNumberOfFiles,
      JsonFileSize(stats.totalBytes),
      fileTypeToProcessingSpeed.getValue(fileType),
      jsonBiggestFileTypeContributors.sortedByDescending { it.partOfTotalProcessingTimeOfThisFileType.doublePercentages }
    )
  }

  val statsPerParentLang = languageMap.map { (lang, data) ->
    JsonStatsPerParentLanguage(language = lang,
                               partOfTotalProcessingTime = calculatePercentages(data.totalProcessingTimeInAllThreads, totalProcessingTime),
                               partOfTotalContentLoadingTime = calculatePercentages(data.totalContentLoadingTime, totalContentLoadingTime),
                               totalNumberOfFiles = data.totalNumberOfFiles,
                               totalFilesSize = JsonFileSize(data.totalBytes),
                               totalProcessingSpeed = JsonProcessingSpeed(data.totalBytes, data.totalProcessingTimeInAllThreads)
    )
  }
  return Pair(statsPerFileTypes, statsPerParentLang)
}

private fun ProjectIndexingHistoryImpl.aggregateStatsPerIndexer(): List<JsonProjectIndexingHistory.JsonStatsPerIndexer> {
  val totalIndexingTime = totalStatsPerIndexer.values.sumOf { it.totalIndexValueChangerEvaluationTimeInAllThreads }
  val indexIdToIndexingTimePart = totalStatsPerIndexer.mapValues {
    calculatePercentages(it.value.totalIndexValueChangerEvaluationTimeInAllThreads, totalIndexingTime)
  }

  val indexIdToIndexValueChangerEvaluationSpeed = totalStatsPerIndexer.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexValueChangerEvaluationTimeInAllThreads)
  }

  return totalStatsPerIndexer.map { (indexId, stats) ->
    JsonProjectIndexingHistory.JsonStatsPerIndexer(
      indexId = indexId,
      partOfTotalIndexingTime = indexIdToIndexingTimePart.getValue(indexId),
      totalNumberOfFiles = stats.totalNumberOfFiles,
      totalNumberOfFilesIndexedByExtensions = stats.totalNumberOfFilesIndexedByExtensions,
      totalFilesSize = JsonFileSize(stats.totalBytes),
      indexValueChangerEvaluationSpeed = indexIdToIndexValueChangerEvaluationSpeed.getValue(indexId),
      snapshotInputMappingStats = JsonProjectIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats(
        totalRequests = stats.snapshotInputMappingStats.requests,
        totalMisses = stats.snapshotInputMappingStats.misses,
        totalHits = stats.snapshotInputMappingStats.hits
      )
    )
  }
}

private fun ProjectDumbIndexingHistoryImpl.aggregateStatsPerIndexer(): List<JsonProjectDumbIndexingHistory.JsonStatsPerIndexer> {
  val totalIndexingTime = totalStatsPerIndexer.values.sumOf { it.totalIndexValueChangerEvaluationTimeInAllThreads }
  val indexIdToIndexingTimePart = totalStatsPerIndexer.mapValues {
    calculatePercentages(it.value.totalIndexValueChangerEvaluationTimeInAllThreads, totalIndexingTime)
  }

  val indexIdToIndexValueChangerEvaluationSpeed = totalStatsPerIndexer.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexValueChangerEvaluationTimeInAllThreads)
  }

  return totalStatsPerIndexer.map { (indexId, stats) ->
    JsonProjectDumbIndexingHistory.JsonStatsPerIndexer(
      indexId = indexId,
      partOfTotalIndexingTime = indexIdToIndexingTimePart.getValue(indexId),
      totalNumberOfFiles = stats.totalNumberOfFiles,
      totalNumberOfFilesIndexedByExtensions = stats.totalNumberOfFilesIndexedByExtensions,
      totalFilesSize = JsonFileSize(stats.totalBytes),
      indexValueChangerEvaluationSpeed = indexIdToIndexValueChangerEvaluationSpeed.getValue(indexId),
      snapshotInputMappingStats = JsonProjectDumbIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats(
        totalRequests = stats.snapshotInputMappingStats.requests,
        totalMisses = stats.snapshotInputMappingStats.misses,
        totalHits = stats.snapshotInputMappingStats.hits
      )
    )
  }
}