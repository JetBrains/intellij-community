// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService

internal class DontShowAgainValueCollector : ApplicationUsagesCollector() {

  private val GROUP = EventLogGroup("feedback.in.ide.dont.show.again.state", 2)

  private val DISABLED_VERSION_GROUP = GROUP.registerEvent(
    "disabledVersions", EventFields.StringListValidatedByRegexp("versionList", "version"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    return setOf(DISABLED_VERSION_GROUP.metric(DontShowAgainFeedbackService.getAllIdeVersionWithDisabledFeedback()))
  }
}