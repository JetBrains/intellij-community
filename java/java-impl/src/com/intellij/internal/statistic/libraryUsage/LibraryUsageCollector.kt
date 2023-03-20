// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.LibraryNameValidationRule
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project

internal class LibraryUsageCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = Holder.GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    return LibraryUsageStatisticsStorageService.getInstance(project)
      .getStatisticsAndResetState()
      .mapNotNullTo(mutableSetOf()) { (usageInfo, count) ->
        val libraryName = usageInfo.name ?: return@mapNotNullTo null
        val libraryVersion = usageInfo.version ?: return@mapNotNullTo null
        val libraryFileTypeString = usageInfo.fileTypeName ?: return@mapNotNullTo null
        val libraryFileType = FileTypeManager.getInstance().findFileTypeByName(libraryFileTypeString) ?: return@mapNotNullTo null

        Holder.EVENT.metric(
          Holder.libraryNameField.with(libraryName),
          Holder.versionField.with(libraryVersion),
          Holder.fileTypeField.with(libraryFileType),
          Holder.countField.with(count),
        )
      }
  }

  private object Holder {
    val GROUP = EventLogGroup("libraryUsage", 4)

    val libraryNameField = EventFields.StringValidatedByCustomRule("library_name", LibraryNameValidationRule::class.java)
    val versionField = EventFields.Version
    val fileTypeField = EventFields.FileType
    val countField = EventFields.Count

    val EVENT = GROUP.registerVarargEvent(
      eventId = "library_used",
      libraryNameField,
      versionField,
      fileTypeField,
      countField,
    )
  }
}
