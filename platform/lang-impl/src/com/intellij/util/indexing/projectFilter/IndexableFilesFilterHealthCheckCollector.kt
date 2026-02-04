// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

/**
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-300/indexable.files.filter">About indexable.files.filter collector</a>
 */
internal object IndexableFilesFilterHealthCheckCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("indexable.files.filter", 10)

  override fun getGroup(): EventLogGroup = GROUP

  private val filterNameField = EventFields.StringValidatedByEnum("filter_name", "indexable_files_filter_name")
  private val attemptNumberInProjectField = EventFields.Int("attempt_number_in_project", "Health check attempt number in a given project. First is 1.")
  private val successfulAttemptNumberInProjectField = EventFields.Int("successful_attempt_number_in_project", "Finished (not-cancelled) health check attempt number in a given project. First is 1.")
  private val durationMsFiled = EventFields.Int("duration_ms", "Health check duration in milliseconds")
  private val cancelledAttemptNumberInProjectField = EventFields.Int("cancelled_attempt_number_in_project", "Cancelled health check attempt number in a given project. First is 1.")
  private val cancellationReasonField = EventFields.Enum("cancellation_reason", FilterActionCancellationReason::class.java)
  private val nonIndexableFilesInFilterField = EventFields.Int("non_indexable_files_in_filter_count")
  private val indexableFilesNotInFilterField = EventFields.Int("indexable_files_not_in_filter_count")

  private val indexableFilesFilterHealthCheck = GROUP.registerVarargEvent(
    "indexable_files_filter_health_check",
    filterNameField,
    attemptNumberInProjectField,
    successfulAttemptNumberInProjectField,
    durationMsFiled,
    nonIndexableFilesInFilterField,
    indexableFilesNotInFilterField,
  )

  private val indexableFilesFilterHealthCheckStarted = GROUP.registerEvent(
    "indexable_files_filter_health_check_started",
    filterNameField,
    attemptNumberInProjectField,
  )

  private val indexableFilesFilterHealthCheckCancelled = GROUP.registerVarargEvent(
    "indexable_files_filter_health_check_cancelled",
    filterNameField,
    attemptNumberInProjectField,
    cancelledAttemptNumberInProjectField,
    durationMsFiled,
    cancellationReasonField,
  )

  fun reportIndexableFilesFilterHealthcheckStarted(project: Project, filter: ProjectIndexableFilesFilter, attemptNumber: Int) {
    indexableFilesFilterHealthCheckStarted.log(project, getFilterName(filter), attemptNumber)
  }

  fun reportIndexableFilesFilterHealthcheck(
    project: Project,
    filter: ProjectIndexableFilesFilter,
    attemptNumber: Int,
    successfulAttemptNumber: Int,
    durationMs: Int,
    nonIndexableFilesInFilterCount: Int,
    indexableFilesNotInFilterCount: Int,
  ) {
    indexableFilesFilterHealthCheck.log(
      project,
      filterNameField.with(getFilterName(filter)),
      attemptNumberInProjectField.with(attemptNumber),
      successfulAttemptNumberInProjectField.with(successfulAttemptNumber),
      durationMsFiled.with(durationMs),
      nonIndexableFilesInFilterField.with(nonIndexableFilesInFilterCount),
      indexableFilesNotInFilterField.with(indexableFilesNotInFilterCount),
    )
  }

  fun reportIndexableFilesFilterHealthcheckCancelled(
    project: Project,
    filter: ProjectIndexableFilesFilter,
    attemptNumber: Int,
    cancelledAttemptNumber: Int,
    durationMs: Int,
    cancellationReason: FilterActionCancellationReason,
  ) {
    indexableFilesFilterHealthCheckCancelled.log(
      project,
      filterNameField.with(getFilterName(filter)),
      attemptNumberInProjectField.with(attemptNumber),
      cancelledAttemptNumberInProjectField.with(cancelledAttemptNumber),
      durationMsFiled.with(durationMs),
      cancellationReasonField.with(cancellationReason)
    )
  }

  private fun getFilterName(filter: ProjectIndexableFilesFilter): String? {
    return when (filter) {
      is CachingProjectIndexableFilesFilter -> "caching"
      is PersistentProjectIndexableFilesFilter -> "persistent"
      is IncrementalProjectIndexableFilesFilter -> "incremental"
      else -> null
    }
  }
}
