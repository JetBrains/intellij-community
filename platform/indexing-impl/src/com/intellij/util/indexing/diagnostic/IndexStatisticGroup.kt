// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.ID
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
object IndexStatisticGroup {
  val GROUP = EventLogGroup("indexing.statistics", 10)

  private val stubIndexInconsistencyRegistered = GROUP.registerEvent("stub.index.inconsistency")

  @JvmStatic
  fun reportStubIndexInconsistencyRegistered(project: Project) {
    stubIndexInconsistencyRegistered.log(project)
  }

  private val indexIdField =
    EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator::class.java)
  private val rebuildCauseField =
    EventFields.Class("rebuild_cause")
  private val insideIndexInitialization =
    EventFields.Boolean("inside_index_initialization")

  private val indexRebuildEvent = GROUP.registerVarargEvent (
    "index_rebuild",
    indexIdField,
    rebuildCauseField,
    insideIndexInitialization,
  )

  @JvmStatic
  fun reportIndexRebuild(indexId: ID<*, *>,
                         cause: Throwable,
                         isInsideIndexInitialization: Boolean) {
    indexRebuildEvent.log(indexIdField with indexId.name,
                          rebuildCauseField with cause.javaClass,
                          insideIndexInitialization with isInsideIndexInitialization)
  }

  enum class IndexingActivityType(val text: String) {
    Scanning("scanning"), DumbIndexing("dumb_indexing");
  }

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

  fun reportActivityStarted(project: Project, indexingSessionId: Long, type: IndexingActivityType) {
    indexingStarted.log(
      project,
      this.indexingSessionId.with(indexingSessionId),
      this.activityType.with(type)
    )
  }

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
  override fun getGroup(): EventLogGroup = IndexStatisticGroup.GROUP
}