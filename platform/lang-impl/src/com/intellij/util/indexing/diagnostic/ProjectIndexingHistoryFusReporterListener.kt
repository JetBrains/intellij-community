// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.util.indexing.diagnostic.IndexStatisticGroup.IndexingActivityType
import com.intellij.util.indexing.diagnostic.dto.toMillis


internal class ProjectIndexingHistoryFusReporterListener : ProjectIndexingActivityHistoryListener {
  override fun onStartedScanning(history: ProjectScanningHistory) {
    IndexStatisticGroup.reportActivityStarted(
      history.project,
      history.indexingActivitySessionId,
      IndexingActivityType.Scanning
    )
  }

  override fun onFinishedScanning(history: ProjectScanningHistory) {
    val times = history.times
    val totalTimeWithPausesMillis = times.totalUpdatingTime.toMillis()
    val pausedDurationMillis = times.pausedDuration.toMillis()
    IndexStatisticGroup.reportScanningFinished(
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
      times.isCancelled
    )
  }

  override fun onStartedDumbIndexing(history: ProjectDumbIndexingHistory) {
    IndexStatisticGroup.reportActivityStarted(
      history.project,
      history.indexingActivitySessionId,
      IndexingActivityType.DumbIndexing
    )
  }

  override fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {
    val times = history.times
    val totalTimeWithPausesMillis = times.totalUpdatingTime.toMillis()
    val pausesMillis = times.pausedDuration.toMillis()
    IndexStatisticGroup.reportDumbIndexingFinished(
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
      // JsonFileProviderIndexStatistics#totalNumberOfNotEvaluatedFiles is not yet reported
      times.isCancelled
    )
  }
}