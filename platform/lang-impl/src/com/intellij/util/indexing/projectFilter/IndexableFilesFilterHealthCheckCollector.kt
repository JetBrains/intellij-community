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
  private val GROUP = EventLogGroup("indexable.files.filter", 6)

  override fun getGroup(): EventLogGroup = GROUP

  private val filterNameField = EventFields.StringValidatedByEnum("filter_name", "indexable_files_filter_name")
  private val attemptNumberInProjectField = EventFields.Int("attempt_number_in_project")
  private val successfulAttemptNumberInProjectField = EventFields.Int("successful_attempt_number_in_project")
  private val nonIndexableFilesInFilterField = EventFields.Int("non_indexable_files_in_filter_count")
  private val indexableFilesNotInFilterField = EventFields.Int("indexable_files_not_in_filter_count")

  private val indexableFilesFilterHealthCheck = GROUP.registerVarargEvent(
    "indexable_files_filter_health_check",
    filterNameField,
    attemptNumberInProjectField,
    successfulAttemptNumberInProjectField,
    nonIndexableFilesInFilterField,
    indexableFilesNotInFilterField,
  )

  private val indexableFilesFilterHealthCheckStarted = GROUP.registerEvent(
    "indexable_files_filter_health_check_started",
    filterNameField,
    attemptNumberInProjectField,
  )

  fun reportIndexableFilesFilterHealthcheckStarted(project: Project,
                                                   filter: ProjectIndexableFilesFilter,
                                                   attemptNumber: Int) {
    indexableFilesFilterHealthCheckStarted.log(
      project,
      getFilterName(filter),
      attemptNumber,
    )
  }


  fun reportIndexableFilesFilterHealthcheck(project: Project,
                                            filter: ProjectIndexableFilesFilter,
                                            attemptNumber: Int,
                                            successfulAttemptNumber: Int,
                                            nonIndexableFilesInFilterCount: Int,
                                            indexableFilesNotInFilterCount: Int) {
    indexableFilesFilterHealthCheck.log(
      project,
      filterNameField.with(getFilterName(filter)),
      attemptNumberInProjectField.with(attemptNumber),
      successfulAttemptNumberInProjectField.with(successfulAttemptNumber),
      nonIndexableFilesInFilterField.with(nonIndexableFilesInFilterCount),
      indexableFilesNotInFilterField.with(indexableFilesNotInFilterCount),
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