// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.analysis

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

public object GenerateLoggerStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("jvm.logger.generation", 2)

  private const val ACTION_STARTED = "action_started"

  private const val ACTION_FINISHED = "action_finished"

  private val ACTION_STATUS: EventField<String?> = EventFields.String("action_status", listOf(ACTION_STARTED, ACTION_FINISHED))

  private val ACTION_INVOKED = GROUP.registerVarargEvent("action.invoked", ACTION_STATUS)

  override fun getGroup(): EventLogGroup = GROUP

  public fun logActionInvoked(project: Project) {
    ACTION_INVOKED.log(project, ACTION_STATUS.with(ACTION_STARTED))
  }

  public fun logActionCompleted(project: Project) {
    ACTION_INVOKED.log(project, ACTION_STATUS.with(ACTION_FINISHED))
  }
}