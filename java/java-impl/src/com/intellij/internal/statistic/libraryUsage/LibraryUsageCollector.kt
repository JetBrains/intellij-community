// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project

internal class LibraryUsageCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP
  override fun getMetrics(project: Project): MutableSet<MetricEvent> = LibraryUsageStatisticsStorageService.getInstance(project)
    .getStatisticsAndResetState()
    .mapNotNullTo(mutableSetOf()) { (usageInfo, count) ->
      val libraryName = usageInfo.name ?: return@mapNotNullTo null
      val libraryVersion = usageInfo.version ?: return@mapNotNullTo null
      val libraryFileTypeString = usageInfo.fileTypeName ?: return@mapNotNullTo null
      val libraryFileType = FileTypeManager.getInstance().findFileTypeByName(libraryFileTypeString) ?: return@mapNotNullTo null

      EVENT.metric(
        libraryNameField.with(libraryName),
        versionField.with(libraryVersion),
        fileTypeField.with(libraryFileType),
        countField.with(count),
      )
    }

  companion object {
    private val GROUP = EventLogGroup("libraryUsage", 3)
    private val libraryNameField = EventFields.String("library_name", emptyList()) // TODO: workaround. Fix after IDEA-279202
    private val versionField = EventFields.Version
    private val fileTypeField = EventFields.FileType
    private val countField = EventFields.Count
    private val EVENT = GROUP.registerVarargEvent(
      eventId = "library_used",
      libraryNameField,
      versionField,
      fileTypeField,
      countField,
    )
  }
}
