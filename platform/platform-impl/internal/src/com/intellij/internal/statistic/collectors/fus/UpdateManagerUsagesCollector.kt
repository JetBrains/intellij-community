// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager

internal class UpdateManagerUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("platform.installer", 3)

  private val MANAGERS = listOf("Toolbox App", "Snap", "Homebrew", "Other", "IDE")

  private val UPDATE_MANAGER = GROUP.registerEvent("Update Manager", EventFields.String("value", MANAGERS))

  override fun getMetrics(): Set<MetricEvent> = setOf(UPDATE_MANAGER.metric(
    when (ExternalUpdateManager.ACTUAL) {
      ExternalUpdateManager.TOOLBOX -> "Toolbox App"
      ExternalUpdateManager.SNAP -> "Snap"
      ExternalUpdateManager.BREW -> "Homebrew"
      ExternalUpdateManager.UNKNOWN -> "Other"
      null -> "IDE"
    }
  ))

  override fun getGroup(): EventLogGroup = GROUP
}
