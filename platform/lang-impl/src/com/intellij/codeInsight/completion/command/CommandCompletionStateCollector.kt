// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class CommandCompletionStateCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("command.completion.state", 1)

  /**
   * Represents the state if the user enabled command completion.
   */
  private val COMPLETION_ENABLED_FIELD: BooleanEventField = BooleanEventField("enabled")

  /**
   * Represents the state if commands completion is shown separately from other completion in group
   */
  private val COMPLETION_USE_GROUP_ENABLED_FIELD: BooleanEventField = BooleanEventField("used_as_group")

  private val COMMAND_COMPLETION_STATE_EVENT: EventId2<Boolean, Boolean> = GROUP.registerEvent("command.completion.enabled", COMPLETION_ENABLED_FIELD, COMPLETION_USE_GROUP_ENABLED_FIELD)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val service = ApplicationCommandCompletionService.getInstance()
    return setOf(
      COMMAND_COMPLETION_STATE_EVENT.metric(service.commandCompletionEnabled(), service.useGroupEnabled())
    )
  }
}