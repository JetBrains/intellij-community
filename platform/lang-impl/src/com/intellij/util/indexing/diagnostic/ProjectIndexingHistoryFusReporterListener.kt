// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.toMillis
import java.util.*

private enum class IndexingActivityType(val text: String) {
  Scanning("scanning"), DumbIndexing("dumb_indexing");
}

internal class ProjectIndexingHistoryFusReporterListener : ProjectIndexingActivityHistoryListener {
  override fun onStartedScanning(history: ProjectScanningHistory) {
    ProjectIndexingHistoryFusHandler.reportActivityStarted(
      history.project,
      history.indexingActivitySessionId,
      IndexingActivityType.Scanning
    )
  }

  override fun onFinishedScanning(history: ProjectScanningHistory) {
    val times = history.times
    val totalTimeWithPausesMillis = times.totalUpdatingTime.toMillis()
    val pausedDurationMillis = times.pausedDuration.toMillis()
    ProjectIndexingHistoryFusHandler.reportScanningFinished(
      history.project,
      history.indexingActivitySessionId,
      history.scanningSessionId,
      times.scanningType,
      pausedDurationMillis != 0L,
      totalTimeWithPausesMillis,
      totalTimeWithPausesMillis - pausedDurationMillis,
      times.dumbModeWithPausesDuration.toMillis(),
      times.dumbModeWithoutPausesDuration.toMillis(),
      history.scanningStatistics.sumOf { statistics -> statistics.numberOfScannedFiles },
      history.scanningStatistics.sumOf { statistics -> statistics.numberOfFilesFullyIndexedByInfrastructureExtensions },
      times.wasInterrupted
    )
  }

  override fun onStartedDumbIndexing(history: ProjectDumbIndexingHistory) {
    ProjectIndexingHistoryFusHandler.reportActivityStarted(
      history.project,
      history.indexingActivitySessionId,
      IndexingActivityType.DumbIndexing
    )
  }

  override fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {
    val times = history.times
    val totalTimeWithPausesMillis = times.totalUpdatingTime.toMillis()
    val pausesMillis = times.pausedDuration.toMillis()
    ProjectIndexingHistoryFusHandler.reportDumbIndexingFinished(
      history.project,
      history.indexingActivitySessionId,
      times.scanningIds.toList(),
      pausesMillis != 0L,
      totalTimeWithPausesMillis,
      totalTimeWithPausesMillis - pausesMillis,
      times.contentLoadingVisibleDuration.toMillis(),
      times.separateValueApplicationVisibleTime.toMillis(),
      history.providerStatistics.sumOf { statistics -> statistics.totalNumberOfIndexedFiles },
      history.providerStatistics.sumOf { statistics -> statistics.totalNumberOfFilesFullyIndexedByExtensions },
      times.wasInterrupted
    )
  }
}

private object ProjectIndexingHistoryFusHandler {
  val GROUP = EventLogGroup("indexing.statistics", 7)

  private val indexingSessionId = EventFields.Long("indexing_session_id")
  private val activityType = EventFields.Enum<IndexingActivityType>("indexing_activity_type") { type -> type.text }
  private val scanningIds = EventFields.LongList("scanning_ids")
  private val scanningType = EventFields.Enum<ScanningType>("type") { type -> type.name.lowercase(Locale.ENGLISH) }
  private val hasPauses = EventFields.Boolean("has_pauses")

  private val totalActivityTime = EventFields.Long("total_activity_time_with_pauses")
  private val totalActivityTimeWithoutPauses = EventFields.Long("total_activity_time_without_pauses")
  private val contentLoadingTimeWithPauses = EventFields.Long("content_loading_time_with_pauses")
  private val indexesWritingTimeWithPauses = EventFields.Long("indexes_writing_time_with_pauses")
  private val dumbTimeWithPauses = EventFields.Long("dumb_time_with_pauses")
  private val dumbTimeWithoutPauses = EventFields.Long("dumb_time_without_pauses")

  private val numberOfHandledFiles = EventFields.Int("number_of_handled_files")
  private val numberOfFilesIndexedByExtensions = EventFields.Int("number_of_files_indexed_by_extensions")
  private val isCancelled = EventFields.Boolean("is_cancelled")

  private val indexingStarted = GROUP.registerVarargEvent(
    "started",
    indexingSessionId,
    activityType
  )

  private val indexingActivityFinished = GROUP.registerVarargEvent(
    "finished",
    indexingSessionId,
    activityType,
    scanningIds,
    scanningType,
    hasPauses,
    totalActivityTime,
    totalActivityTimeWithoutPauses,
    contentLoadingTimeWithPauses,
    indexesWritingTimeWithPauses,
    dumbTimeWithPauses,
    dumbTimeWithoutPauses,
    numberOfHandledFiles,
    numberOfFilesIndexedByExtensions,
    isCancelled
  )

  fun reportActivityStarted(project: Project, indexingSessionId: Long, type: IndexingActivityType) {
    indexingStarted.log(
      project,
      this.indexingSessionId.with(indexingSessionId),
      this.activityType.with(type)
    )
  }

  fun reportScanningFinished(
    project: Project,
    indexingSessionId: Long,
    scanningId: Long,
    scanningType: ScanningType,
    hasPauses: Boolean,
    totalTimeWithPauses: Long,
    totalTimeWithoutPauses: Long,
    dumbTimeWithPauses: Long,
    dumbTimeWithoutPauses: Long,
    numberOfHandledFiles: Int,
    numberOfFilesIndexedByExtensions: Int,
    isCancelled: Boolean
  ) {
    indexingActivityFinished.log(
      project,
      this.indexingSessionId.with(indexingSessionId),
      this.activityType.with(IndexingActivityType.Scanning),
      this.scanningIds.with(listOf(scanningId)),
      this.scanningType.with(scanningType),
      this.hasPauses.with(hasPauses),
      this.totalActivityTime.with(totalTimeWithPauses),
      this.totalActivityTimeWithoutPauses.with(totalTimeWithoutPauses),
      this.dumbTimeWithPauses.with(dumbTimeWithPauses),
      this.dumbTimeWithoutPauses.with(dumbTimeWithoutPauses),
      this.numberOfHandledFiles.with(numberOfHandledFiles),
      this.numberOfFilesIndexedByExtensions.with(StatisticsUtil.roundToHighestDigit(numberOfFilesIndexedByExtensions)),
      this.isCancelled.with(isCancelled)
    )
  }

  fun reportDumbIndexingFinished(
    project: Project,
    indexingSessionId: Long,
    scanningIds: List<Long>,
    hasPauses: Boolean,
    totalTimeWithPauses: Long,
    totalTimeWithoutPauses: Long,
    contentLoadingTimeWithPauses: Long,
    indexingWritingTimeWithPauses: Long,
    numberOfHandledFiles: Int,
    numberOfFilesIndexedByExtensions: Int,
    isCancelled: Boolean
  ) {
    indexingActivityFinished.log(
      project,
      this.indexingSessionId.with(indexingSessionId),
      this.activityType.with(IndexingActivityType.DumbIndexing),
      this.scanningIds.with(scanningIds),
      this.hasPauses.with(hasPauses),
      this.totalActivityTime.with(totalTimeWithPauses),
      this.totalActivityTimeWithoutPauses.with(totalTimeWithoutPauses),
      this.contentLoadingTimeWithPauses.with(contentLoadingTimeWithPauses),
      this.indexesWritingTimeWithPauses.with(indexingWritingTimeWithPauses),
      this.dumbTimeWithPauses.with(totalTimeWithPauses),
      this.dumbTimeWithoutPauses.with(totalTimeWithoutPauses),
      this.numberOfHandledFiles.with(numberOfHandledFiles),
      this.numberOfFilesIndexedByExtensions.with(StatisticsUtil.roundToHighestDigit(numberOfFilesIndexedByExtensions)),
      this.isCancelled.with(isCancelled)
    )
  }
}

class ProjectIndexingHistoryFusReporter : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = ProjectIndexingHistoryFusHandler.GROUP
}