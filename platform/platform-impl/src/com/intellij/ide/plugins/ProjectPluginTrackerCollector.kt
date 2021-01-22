// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

class ProjectPluginTrackerCollector : ApplicationUsagesCollector() {

  companion object {

    @JvmStatic
    private val Group = EventLogGroup(
      "plugins.per.project",
      1,
    )

    private val Count = Group.registerEvent(
      "plugins.per.project.count",
      IntEventField("enabled"),
      IntEventField("disabled"),
    )
  }

  override fun getMetrics(): Set<MetricEvent> {
    return ProjectPluginTrackerManager
      .getInstance()
      .statesByProject
      .mapNotNullTo(HashSet()) {
        val state = it.value
        val enabledCount = state.enabledPlugins.size
        val disabledCount = state.disabledPlugins.size

        if (enabledCount == 0 || disabledCount == 0) null
        else Count.metric(enabledCount, disabledCount)
      }
  }

  override fun getGroup() = Group
}