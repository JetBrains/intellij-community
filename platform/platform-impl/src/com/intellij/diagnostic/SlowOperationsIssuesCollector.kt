// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByInlineRegexp
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.util.SlowOperations

internal object SlowOperationsIssuesCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("slow.operations", 1)

  private val ISSUE_TRIGGERED = GROUP.registerEvent("issue.triggered", StringListValidatedByInlineRegexp("issue_id", "\\b[A-Z]+-\\d+\\b"))

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val knownIssues = SlowOperations.reportKnownIssues()
    return knownIssues
      .flatMap { ytCodes -> ytCodes.split(",") }
      .map { it.trim() }
      .map { ISSUE_TRIGGERED.metric(listOf(it)) }
      .toMutableSet()
  }
}
