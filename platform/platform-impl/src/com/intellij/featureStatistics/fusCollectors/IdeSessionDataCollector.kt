// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager

internal class IdeSessionDataCollector : ApplicationUsagesCollector(), AllowedDuringStartupCollector {
  private val GROUP = EventLogGroup("event.log.session", 1)
  private val DEBUG = GROUP.registerEvent("debug.mode", Boolean("debug_agent"))
  private val REPORT = GROUP.registerEvent("reporting", Boolean("suppress_report"), Boolean("only_local"))
  private val TEST = GROUP.registerEvent("test.mode", Boolean("fus_test"), Boolean("internal"), Boolean("teamcity"))
  private val HEADLESS = GROUP.registerEvent("headless", Boolean("headless"), Boolean("command_line"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> =
    ApplicationManager.getApplication().let { app ->
      setOf(
        DEBUG.metric(DebugAttachDetector.isDebugEnabled()),
        REPORT.metric(StatisticsUploadAssistant.isSuppressStatisticsReport(), StatisticsUploadAssistant.isLocalStatisticsWithoutReport()),
        TEST.metric(StatisticsRecorderUtil.isTestModeEnabled("FUS"), app.isInternal, StatisticsUploadAssistant.isTeamcityDetected()),
        HEADLESS.metric(app.isHeadlessEnvironment, app.isCommandLine))
    }
}
