// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.statistics

import com.intellij.feedback.common.state.DontShowAgainFeedbackService
import com.intellij.feedback.common.state.DontShowAgainFeedbackState
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

class DontShowAgainValueCollector : ApplicationUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("feedback.in.ide.dont.show.again.state", 2)

    private val DISABLED_VERSION_GROUP = GROUP.registerEvent(
      "disabledVersions", EventFields.StringListValidatedByRegexp("versionList", "version"))
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val state: DontShowAgainFeedbackState = DontShowAgainFeedbackService.getInstance().state
    return setOf(DISABLED_VERSION_GROUP.metric(state.dontShowAgainIdeVersions.toList()))
  }
}