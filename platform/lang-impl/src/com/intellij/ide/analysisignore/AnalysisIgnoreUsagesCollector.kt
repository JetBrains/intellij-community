// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus

/**
 * Reports the state of the `.analysisignore` feature in a project.
 */
@ApiStatus.Internal
class AnalysisIgnoreUsagesCollector : ProjectUsagesCollector() {
  private val group = EventLogGroup("analysisignore", 1)

  private val featureEnabled = group.registerEvent("feature.enabled", EventFields.Enabled)
  private val filesFound = group.registerEvent("files.found", EventFields.RoundedInt("count"))

  override fun getGroup(): EventLogGroup = group

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val enabled = Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)
    return buildSet {
      add(featureEnabled.metric(enabled))
      if (enabled) {
        val count = WorkspaceModel.getInstance(project).currentSnapshot.entities<AnalysisIgnoreEntity>().count()
        add(filesFound.metric(count))
      }
    }
  }
}
