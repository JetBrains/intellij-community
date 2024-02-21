// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object IndexableFilesFilterHealthCheckCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("indexable.files.filter", 2)

  override fun getGroup(): EventLogGroup = GROUP

  private val isOnProjectOpenField = EventFields.Boolean("is_on_project_open")
  private val nonIndexableFilesFoundInFilterField = EventFields.Int("non_indexable_files_found_in_filter_count")
  private val indexableFilesNotFoundInFilterField = EventFields.Int("indexable_files_not_found_in_filter_count")
  private val excludedFilesCountField = EventFields.Int("excluded_files_count")
  private val excludedFilesWereFilteredOutField = EventFields.Boolean("excluded_files_were_filtered_out")

  private val indexableFilesFilterHealthCheck = GROUP.registerVarargEvent(
    "indexable_files_filter_health_check",
    isOnProjectOpenField,
    nonIndexableFilesFoundInFilterField,
    indexableFilesNotFoundInFilterField,
    excludedFilesWereFilteredOutField,
    excludedFilesCountField,
  )

  fun reportIndexableFilesFilterHealthcheck(project: Project,
                                            onProjectOpen: Boolean,
                                            nonIndexableFoundInFilterCount: Int,
                                            indexableNotFoundInFilterCount: Int,
                                            excludedFilesWereFilteredOut: Boolean,
                                            excludedFilesCount: Int) {
    indexableFilesFilterHealthCheck.log(
      project,
      isOnProjectOpenField.with(onProjectOpen),
      nonIndexableFilesFoundInFilterField.with(nonIndexableFoundInFilterCount),
      indexableFilesNotFoundInFilterField.with(indexableNotFoundInFilterCount),
      excludedFilesWereFilteredOutField.with(excludedFilesWereFilteredOut),
      excludedFilesCountField.with(excludedFilesCount),
    )
  }
}