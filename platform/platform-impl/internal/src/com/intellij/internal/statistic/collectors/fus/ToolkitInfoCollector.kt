// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.util.system.OS
import java.awt.Toolkit

internal class ToolkitInfoCollector : ApplicationUsagesCollector() {

  private val GROUP = EventLogGroup("toolkit.info", 2, "FUS", "Toolkit information")
  private val selectedToolkit = GROUP.registerEvent("awt.toolkit.name", EventFields.String("value", listOf("WLToolkit", "XToolkit", "auto", "other", "not_set")), "The value of the awt.toolkit.name VM option")
  private val effectiveToolkit = GROUP.registerEvent("awt.toolkit.effective", EventFields.String("value", listOf("WLToolkit", "XToolkit", "other")), "The effective toolkit used by the IDE")

  override fun getMetrics(): Set<MetricEvent> {
    if (OS.CURRENT != OS.Linux) {
      return emptySet()
    }

    val propValue = when (System.getProperty("awt.toolkit.name")) {
      null -> "not_set"
      "auto" -> "auto"
      "WLToolkit" -> "WLToolkit"
      "XToolkit" -> "XToolkit"
      else -> "other"
    }

    val effectiveValue = when (Toolkit.getDefaultToolkit()?.javaClass?.name) {
      "sun.awt.X11.XToolkit" -> "XToolkit"
      "sun.awt.wl.WLToolkit" -> "WLToolkit"
      else -> "other"
    }

    return setOf(selectedToolkit.metric(propValue), effectiveToolkit.metric(effectiveValue))
  }

  override fun getGroup(): EventLogGroup = GROUP
}