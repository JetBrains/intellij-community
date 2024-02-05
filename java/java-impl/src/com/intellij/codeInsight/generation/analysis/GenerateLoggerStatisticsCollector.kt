// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.analysis

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object GenerateLoggerStatisticsCollector : CounterUsagesCollector() {
  private val group = EventLogGroup("jvm.logger.generation", 1)

  private const val ACTION_INVOKED = "action_invoked"

  private const val ACTION_COMPLETED = "action_completed"

  private val ACTION_TYPE: EventField<String?> = EventFields.String("event_type", listOf(ACTION_INVOKED, ACTION_COMPLETED))

  private val HANDLE_EVENT = group.registerVarargEvent("handle",
                                                       ACTION_TYPE,
                                                       EventFields.Count)

  override fun getGroup(): EventLogGroup = group

  fun logActionInvoked(project: Project) {
    HANDLE_EVENT.log(project, ACTION_TYPE.with(ACTION_INVOKED), EventFields.Count.with(1))
  }

  fun logActionCompleted(project: Project) {
    HANDLE_EVENT.log(project, ACTION_TYPE.with(ACTION_COMPLETED), EventFields.Count.with(1))
  }
}