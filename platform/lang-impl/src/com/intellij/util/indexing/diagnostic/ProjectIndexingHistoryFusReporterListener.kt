// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit

class ProjectIndexingHistoryFusReporterListener : IndexDiagnosticDumper.ProjectIndexingHistoryListener {
  override fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    ProjectIndexingHistoryFusReporter.reportIndexingStarted(
      projectIndexingHistory.project,
      projectIndexingHistory.indexingSessionId
    )
  }

  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    val scanningTime = projectIndexingHistory.times.scanFilesDuration.toMillis()
    val numberOfFileProviders = projectIndexingHistory.scanningStatistics.size
    val numberOfScannedFiles = projectIndexingHistory.scanningStatistics.sumBy { it.numberOfScannedFiles }

    val numberOfFilesIndexedByExtensionsDuringScan =
      projectIndexingHistory.scanningStatistics.sumOf { it.numberOfFilesFullyIndexedByInfrastructureExtensions }
    val numberOfFilesIndexedByExtensionsWithLoadingContent =
      projectIndexingHistory.providerStatistics.sumOf { it.totalNumberOfFilesFullyIndexedByExtensions }
    val numberOfFilesIndexedWithLoadingContent = projectIndexingHistory.providerStatistics.sumOf { it.totalNumberOfIndexedFiles }

    ProjectIndexingHistoryFusReporter.reportIndexingFinished(
      projectIndexingHistory.project,
      projectIndexingHistory.indexingSessionId,
      TimeUnit.NANOSECONDS.toMillis(projectIndexingHistory.times.totalUpdatingTime),
      scanningTime,
      numberOfFileProviders,
      numberOfScannedFiles,
      numberOfFilesIndexedByExtensionsDuringScan,
      numberOfFilesIndexedByExtensionsWithLoadingContent,
      numberOfFilesIndexedWithLoadingContent
    )
  }
}

object ProjectIndexingHistoryFusReporter : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("indexing.statistics", 1)

  override fun getGroup() = GROUP

  private val indexingSessionId = EventFields.Long("indexing_session_id")

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

  private val indexingStarted = GROUP.registerVarargEvent(
    "started",
    indexingSessionId
  )

  private val indexingFinished = GROUP.registerVarargEvent(
    "finished",
    indexingSessionId,
    indexingTime,
    scanningTime,
    numberOfFileProviders,
    numberOfScannedFiles,
    numberOfFilesIndexedByExtensionsDuringScan,
    numberOfFilesIndexedByExtensionsWithLoadingContent,
    numberOfFilesIndexedWithLoadingContent
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
    indexingTime: Long,
    scanningTime: Long,
    numberOfFileProviders: Int,
    numberOfScannedFiles: Int,
    numberOfFilesIndexedByExtensionsDuringScan: Int,
    numberOfFilesIndexedByExtensionsWithLoadingContent: Int,
    numberOfFilesIndexedWithLoadingContent: Int
  ) {
    indexingFinished.log(
      project,
      this.indexingSessionId.with(indexingSessionId),
      this.indexingTime.with(indexingTime),
      this.scanningTime.with(scanningTime),
      this.numberOfFileProviders.with(numberOfFileProviders),
      this.numberOfScannedFiles.with(StatisticsUtil.getNextPowerOfTwo(numberOfScannedFiles)),
      this.numberOfFilesIndexedByExtensionsDuringScan.with(numberOfFilesIndexedByExtensionsDuringScan),
      this.numberOfFilesIndexedByExtensionsWithLoadingContent.with(numberOfFilesIndexedByExtensionsWithLoadingContent),
      this.numberOfFilesIndexedWithLoadingContent.with(numberOfFilesIndexedWithLoadingContent),
    )
  }

}