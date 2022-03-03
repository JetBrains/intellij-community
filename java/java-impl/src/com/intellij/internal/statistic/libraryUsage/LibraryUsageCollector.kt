// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

internal class LibraryUsageCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "libraryUsage"
  override fun getVersion(): Int = 1

  override fun getMetrics(project: Project): Set<MetricEvent> {
    return LibraryUsageStatisticsStorageService.getInstance(project)
      .getStatisticsAndResetState()
      .mapNotNullTo(mutableSetOf()) { (usageInfo, count) ->
        val libraryName = usageInfo.name ?: return@mapNotNullTo null
        val libraryVersion = usageInfo.version ?: return@mapNotNullTo null
        val libraryFileType = usageInfo.fileTypeName ?: return@mapNotNullTo null

        val data = FeatureUsageData().apply {
          addData("library_name", libraryName)
          addVersionByString(libraryVersion)
          addData(EventFields.FileType.name, libraryFileType)
          addCount(count)
          addProject(project)
        }

        MetricEvent("library_used", data)
      }
  }
}