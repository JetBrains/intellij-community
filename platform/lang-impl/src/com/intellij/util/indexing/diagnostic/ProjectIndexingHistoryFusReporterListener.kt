// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.toMillis
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

internal class ProjectIndexingHistoryFusReporterListener : ProjectIndexingHistoryListener {
  override fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    ProjectIndexingHistoryFusReporter.reportIndexingStarted(
      projectIndexingHistory.project,
      projectIndexingHistory.indexingSessionId
    )
  }

  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    val scanningTime = projectIndexingHistory.times.scanFilesDuration.toMillis()
    val numberOfFileProviders = projectIndexingHistory.scanningStatistics.size
    val numberOfScannedFiles = projectIndexingHistory.scanningStatistics.sumOf { it.numberOfScannedFiles }

    val numberOfFilesIndexedByExtensionsDuringScan =
      projectIndexingHistory.scanningStatistics.sumOf { it.numberOfFilesFullyIndexedByInfrastructureExtensions }
    val numberOfFilesIndexedByExtensionsWithLoadingContent =
      projectIndexingHistory.providerStatistics.sumOf { it.totalNumberOfFilesFullyIndexedByExtensions }
    val numberOfFilesIndexedWithLoadingContent = projectIndexingHistory.providerStatistics.sumOf { it.totalNumberOfIndexedFiles }

    val totalContentLoadingTime = projectIndexingHistory.totalStatsPerFileType.values.sumOf { it.totalContentLoadingTimeInAllThreads }
    val totalContentData = projectIndexingHistory.totalStatsPerFileType.values.sumOf { it.totalBytes }
    val averageContentLoadingSpeed = calculateReadSpeed(totalContentData, totalContentLoadingTime)

    val contentLoadingSpeedByFileType = HashMap<FileType, Long>()
    projectIndexingHistory.totalStatsPerFileType.forEach { (fileType, stats) ->
      if (stats.totalContentLoadingTimeInAllThreads != 0L && stats.totalBytes != 0L) {
        contentLoadingSpeedByFileType[FileTypeManager.getInstance().getStdFileType(fileType)] =
          calculateReadSpeed(stats.totalBytes, stats.totalContentLoadingTimeInAllThreads)
      }
    }

    ProjectIndexingHistoryFusReporter.reportIndexingFinished(
      projectIndexingHistory.project,
      projectIndexingHistory.indexingSessionId,
      projectIndexingHistory.times.scanningType,
      projectIndexingHistory.times.totalUpdatingTime.toMillis(),
      projectIndexingHistory.times.indexingDuration.toMillis(),
      scanningTime,
      numberOfFileProviders,
      numberOfScannedFiles,
      numberOfFilesIndexedByExtensionsDuringScan,
      numberOfFilesIndexedByExtensionsWithLoadingContent,
      numberOfFilesIndexedWithLoadingContent,
      averageContentLoadingSpeed,
      contentLoadingSpeedByFileType
    )
  }

  /**
   * @return speed as bytes per second
   * */
  private fun calculateReadSpeed(bytes: BytesNumber, loadingTime: TimeNano): Long {
    if (bytes == 0L || loadingTime == 0L) return 0L

    val nanoSecondInOneSecond = TimeUnit.SECONDS.toNanos(1)
    return if (bytes * nanoSecondInOneSecond > 0) // avoid hitting overflow; possible if loaded more than 9 223 372 037 bytes
    // as `loadingTime` in nanoseconds tend to be much bigger value than `bytes` prefer to divide as second step
      (bytes * nanoSecondInOneSecond) / loadingTime
    else // do not use by default to avoid unnecessary conversions
      ((bytes.toDouble() / loadingTime) * nanoSecondInOneSecond).roundToLong()
  }
}

class ProjectIndexingHistoryFusReporter : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("indexing.statistics", 6)

    private val indexingSessionId = EventFields.Long("indexing_session_id")

    private val isFullRescanning = EventFields.Boolean("is_full")
    private val scanningType = EventFields.Enum<ScanningType>("type") { type -> type.name.lowercase(Locale.ENGLISH) }
    private val totalTime = EventFields.Long("total_time")
    private val indexingTime = EventFields.Long("indexing_time")
    private val scanningTime = EventFields.Long("scanning_time")
    private val numberOfFileProviders = EventFields.Int("number_of_file_providers")
    private val numberOfScannedFiles = EventFields.Int("number_of_scanned_files")

    private val numberOfFilesIndexedByExtensionsDuringScan =
      EventFields.Int("number_of_files_indexed_by_extensions_during_scan")
    private val numberOfFilesIndexedByExtensionsWithLoadingContent =
      EventFields.Int("number_of_files_indexed_by_extensions_with_loading_content")
    private val numberOfFilesIndexedWithLoadingContent =
      EventFields.Int("number_of_files_indexed_with_loading_content")

    private val averageContentLoadingSpeed = EventFields.Long("average_content_loading_speed_bps")
    private val contentLoadingSpeedForFileType = EventFields.Long("average_content_loading_speed_for_file_type_bps")
    private val contentLoadingSpeedByFileType =
      ObjectListEventField("average_content_loading_speeds_by_file_type", EventFields.FileType, contentLoadingSpeedForFileType)

    private val indexingStarted = GROUP.registerVarargEvent(
      "started",
      indexingSessionId
    )

    private val indexingFinished = GROUP.registerVarargEvent(
      "finished",
      indexingSessionId,
      isFullRescanning,
      scanningType,
      totalTime,
      indexingTime,
      scanningTime,
      numberOfFileProviders,
      numberOfScannedFiles,
      numberOfFilesIndexedByExtensionsDuringScan,
      numberOfFilesIndexedByExtensionsWithLoadingContent,
      numberOfFilesIndexedWithLoadingContent,
      averageContentLoadingSpeed,
      contentLoadingSpeedByFileType
    )

    fun reportIndexingStarted(project: Project, indexingSessionId: Long) {
      indexingStarted.log(
        project,
        this.indexingSessionId.with(indexingSessionId)
      )
    }

    fun reportIndexingFinished(
      project: Project,
      indexingSessionId: Long,
      scanningType: ScanningType,
      totalTime: Long,
      indexingTime: Long,
      scanningTime: Long,
      numberOfFileProviders: Int,
      numberOfScannedFiles: Int,
      numberOfFilesIndexedByExtensionsDuringScan: Int,
      numberOfFilesIndexedByExtensionsWithLoadingContent: Int,
      numberOfFilesIndexedWithLoadingContent: Int,
      averageContentLoadingSpeed: Long,
      contentLoadingSpeedByFileType: Map<FileType, Long>

    ) {
      indexingFinished.log(
        project,
        this.indexingSessionId.with(indexingSessionId),
        this.isFullRescanning.with(scanningType.isFull),
        this.scanningType.with(scanningType),
        this.totalTime.with(totalTime),
        this.indexingTime.with(indexingTime),
        this.scanningTime.with(scanningTime),
        this.numberOfFileProviders.with(numberOfFileProviders),
        this.numberOfScannedFiles.with(StatisticsUtil.roundToHighestDigit(numberOfScannedFiles)),
        this.numberOfFilesIndexedByExtensionsDuringScan.with(StatisticsUtil.roundToHighestDigit(numberOfFilesIndexedByExtensionsDuringScan)),
        this.numberOfFilesIndexedByExtensionsWithLoadingContent.with(
          StatisticsUtil.roundToHighestDigit(numberOfFilesIndexedByExtensionsWithLoadingContent)),
        this.numberOfFilesIndexedWithLoadingContent.with(StatisticsUtil.roundToHighestDigit(numberOfFilesIndexedWithLoadingContent)),
        this.averageContentLoadingSpeed.with(averageContentLoadingSpeed),
        this.contentLoadingSpeedByFileType.with(contentLoadingSpeedByFileType.map { entry ->
          ObjectEventData(EventFields.FileType.with(entry.key), contentLoadingSpeedForFileType.with(entry.value))
        })
      )
    }
  }

  override fun getGroup() = GROUP
}