// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.project.getFileTypeWithoutContent
import com.intellij.internal.statistic.project.processContentFilesInReadActions
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

internal class CodeInsightContextFusCollector : ProjectUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("code.insight.context", 1)

  private val ENABLED = GROUP.registerEvent("shared_source_support_enabled", EventFields.Boolean("enabled"))

  private val CONTEXT_REPORT = GROUP.registerEvent(
    "context_report",
    EventFields.FileType, EventFields.Int("context_number"), EventFields.RoundedInt("file_number")
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    if (project.isDisposed) return emptySet()

    val sharedSourceEnabled = isSharedSourceSupportEnabled(project)
    val enabledEvent = ENABLED.metric(sharedSourceEnabled)
    if (!sharedSourceEnabled) return setOf(enabledEvent)

    val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
    val fileCounts = HashMap<FileType, Int2IntOpenHashMap>()
    processContentFilesInReadActions(project) { file ->
      val contextCount = contextManager.getCodeInsightContextsWithoutCaching(file).size
      fileCounts.getOrPut(getFileTypeWithoutContent(file)) { Int2IntOpenHashMap() }.addTo(contextCount, 1)
    }

    return buildSet {
      add(enabledEvent)
      for ([fileType, fileCountByContextCount] in fileCounts) {
        for (entry in fileCountByContextCount.int2IntEntrySet()) {
          add(CONTEXT_REPORT.metric(fileType, entry.intKey, entry.intValue))
        }
      }
    }
  }
}
