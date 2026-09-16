// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.project

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

/**
 * Reports the size of a project on disk, and the line count that the size implies.
 *
 * These two measures let an analyst group a metric by repository scale. The platform already reports the file count,
 * the languages and the module totals, so this collector adds only what is missing. See `project.indexable.files`,
 * `file.types` and `project.structure`.
 *
 * The platform adds an anonymized `project` field to every metric of a project state collector. That field is the
 * join key: it carries the same value in every group of the FUS recorder, so an agent event that also reports the
 * field can be joined to this row.
 */
internal class ProjectSizeUsagesCollector : ProjectUsagesCollector() {
  private val group = EventLogGroup("project.size", 1)

  private val locEvent = group.registerEvent("lines.of.code", EventFields.LogarithmicInt("loc"))
  private val sizeEvent = group.registerEvent("size.on.disk", EventFields.LogarithmicInt("repo_size_mb"))
  private val limitReachedEvent = group.registerEvent("files.limit.reached")

  override fun getGroup(): EventLogGroup = group

  override suspend fun collect(project: Project): Set<MetricEvent> {
    val size = computeProjectSize(project)
    return buildSet {
      add(locEvent.metric(size.estimatedLoc))
      add(sizeEvent.metric(size.sizeMb))
      // Report the cap only when it applies, so the absence of the metric means the two numbers are complete.
      if (size.limitReached) add(limitReachedEvent.metric())
    }
  }
}
