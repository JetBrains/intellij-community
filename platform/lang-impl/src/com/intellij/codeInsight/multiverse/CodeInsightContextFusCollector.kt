// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

internal class CodeInsightContextFusCollector : ProjectUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("code.insight.context", 1)

  private val ENABLED = GROUP.registerEvent("shared_source_support_enabled", EventFields.Boolean("enabled"))

  private val CONTEXT_REPORT = GROUP.registerEvent(
    "context_report",
    EventFields.FileType, EventFields.Int("context_number"), EventFields.RoundedInt("file_number")
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun requiresReadAccess(): Boolean = true

  override fun getMetrics(project: Project): Set<MetricEvent> {

    val events = mutableSetOf<MetricEvent>()

    val sharedSourceEnabled = isSharedSourceSupportEnabled(project)

    events.add(ENABLED.metric(sharedSourceEnabled))

    if (sharedSourceEnabled) {
      val contextManager = CodeInsightContextManager.getInstance(project)
      val map = mutableMapOf<FileType, Int2IntMap>()

      ProjectFileIndex.getInstance(project).iterateContent { file ->
        val size = contextManager.getCodeInsightContexts(file).size
        val fileType = file.fileType

        map.getOrPut(fileType) { Int2IntOpenHashMap() }.mergeInt(size, 1, Int::plus)
        true
      }

      map.entries.flatMapTo(events) { (fileType, contextNumber2fileNumber) ->
        ProgressManager.checkCanceled()

        contextNumber2fileNumber.int2IntEntrySet().map<Int2IntMap.Entry, MetricEvent> { entry ->
          CONTEXT_REPORT.metric(fileType, entry.intKey, entry.intValue)
        }
      }
    }

    return events
  }
}
