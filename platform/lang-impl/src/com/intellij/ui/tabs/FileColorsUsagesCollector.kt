// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.ui.FileColorManager

internal class FileColorsUsagesCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("appearance.file.colors", 2)

  private val FILE_COLORS: EventId1<Boolean> = GROUP.registerEvent("file.colors", EventFields.Enabled)
  private val EDITOR_TABS: EventId1<Boolean> = GROUP.registerEvent("editor.tabs", EventFields.Enabled)
  private val PROJECT_VIEW: EventId1<Boolean> = GROUP.registerEvent("project.view", EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = mutableSetOf<MetricEvent>()
    val manager = FileColorManager.getInstance(project) ?: return set
    val enabledFileColors = manager.isEnabled
    val useInEditorTabs = enabledFileColors && manager.isEnabledForTabs
    val useInProjectView = enabledFileColors && manager.isEnabledForProjectView
    if (!enabledFileColors) set.add(FILE_COLORS.metric(false))
    if (!useInEditorTabs) set.add(EDITOR_TABS.metric(false))
    if (!useInProjectView) set.add(PROJECT_VIEW.metric(false))
    return set
  }
}
