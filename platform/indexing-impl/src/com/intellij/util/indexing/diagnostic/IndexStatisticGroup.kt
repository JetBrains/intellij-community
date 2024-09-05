// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.collectors.fus.PluginIdRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.ExceptionUtil
import com.intellij.util.indexing.FileBasedIndex.RebuildRequestedByUserAction
import com.intellij.util.indexing.ID
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Internal
object IndexStatisticGroup {
  internal val GROUP = EventLogGroup("indexing.statistics", 18)

  private val indexIdField = EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator::class.java)
  private val rebuildCauseField = EventFields.Class("rebuild_cause")
  private val requestorPluginId = EventFields.StringValidatedByCustomRule(
    "requestor_plugin_id",
    PluginIdRuleValidator::class.java,
  )
  private val insideIndexInitialization = EventFields.Boolean("inside_index_initialization")

  private val indexRebuildEvent = GROUP.registerVarargEvent(
    "index_rebuild",
    indexIdField,
    rebuildCauseField,
    requestorPluginId,
    insideIndexInitialization,
  )

  @JvmStatic
  fun reportIndexRebuild(
    indexId: ID<*, *>,
    cause: Throwable,
    isInsideIndexInitialization: Boolean,
  ) {
    val realCause = ExceptionUtil.getRootCause(cause)
    val causeClass = realCause.javaClass

    var requestorPluginID: PluginId? = null
    if (realCause is RebuildRequestedByUserAction) {
      requestorPluginID = realCause.requestorPluginId
    }

    indexRebuildEvent.log(indexIdField with indexId.name,
                          rebuildCauseField with causeClass,
                          requestorPluginId with requestorPluginID?.idString,
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
    isCancelled: Boolean,
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
    isCancelled: Boolean,
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

  /* ====================== internal (technical) indexing statistics: ============================================ */

  /** How long the whole indexing takes -- a wall time, ms*/
  private val totalIndexingDurationMs = EventFields.Long("total_indexing_ms")

  /** Total time (ms) indexers were idle (suspended, slept) during indexing, due to backpressure from index writers */
  private val totalTimeIndexersSleptMs = EventFields.Long("indexers_slept_ms")

  /** How many indexers were in play (it depends on user hardware) */
  private val indexersCount = EventFields.Int("indexers")

  /** How many CPUs a user machine has */
  private val cpusCount = EventFields.Int("cpus")

  /** How many files were indexed in this indexing run */
  private val totalFilesIndexed = EventFields.Int("files")

  /**
   * `=indexers_slept_ms/total_indexing_ms/indexersCount`.
   * (It could be calculated from other fields, but not in FUS web UI)
   */
  private val fractionOfTimeSlept = EventFields.Double("fraction_of_time_slept")

  private val indexingInternalStatistics = GROUP.registerVarargEvent(
    "indexing_run_internal_statistics",
    totalIndexingDurationMs,
    totalTimeIndexersSleptMs,
    indexersCount,
    cpusCount,
    totalFilesIndexed,
    fractionOfTimeSlept
  )

  @JvmStatic
  fun reportIndexingInternalStatistics(
    totalFiles: Int,
    indexersCount: Int,
    cpusCount: Int = Runtime.getRuntime().availableProcessors(),
    totalIndexingDuration: Duration,
    indexersSlept: Duration,
  ) {
    if( totalIndexingDuration < 10.seconds ) {
      //short indexing runs are not representative -- queueing effects are too significant
      return
    }

    val fractionOfTimeSlept = indexersSlept * 1.0 / indexersCount / totalIndexingDuration
    indexingInternalStatistics.log(
      totalIndexingDurationMs.with(totalIndexingDuration.inWholeMilliseconds),
      totalTimeIndexersSleptMs.with(indexersSlept.inWholeMilliseconds),
      this.indexersCount.with(indexersCount),
      this.cpusCount.with(cpusCount),
      totalFilesIndexed.with(totalFiles),
      this.fractionOfTimeSlept.with(fractionOfTimeSlept)
    )
  }
}

@Internal
class ProjectIndexingHistoryFusReporter : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = IndexStatisticGroup.GROUP
}