// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

internal class HighlightingSettingsPerFileCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("highlighting.settings.per.file", 2)
  private val SKIP_HIGHLIGHTING_ROOTS = GROUP.registerEvent("skip.highlighting.roots", EventFields.Count)
  private val SKIP_INSPECTION_ROOTS = GROUP.registerEvent("skip.inspection.roots", EventFields.Count)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val settings = HighlightingSettingsPerFile.getInstance(project)
    return setOf(
      SKIP_HIGHLIGHTING_ROOTS.metric(settings.countRoots(FileHighlightingSetting.SKIP_HIGHLIGHTING)),
      SKIP_INSPECTION_ROOTS.metric(settings.countRoots(FileHighlightingSetting.SKIP_INSPECTION))
    )
  }
}
