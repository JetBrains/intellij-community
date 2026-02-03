// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

object ExternalSystemSourceAttachCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("build.tools.sources", 1)

  private val HANDLER_FIELD = EventFields.Class("handler")
  private val SUCCESS_FIELD = EventFields.Boolean("success")

  private val SOURCES_ATTACHED_EVENT = GROUP.registerVarargEvent("attached", HANDLER_FIELD, EventFields.Language, SUCCESS_FIELD,
                                                                 EventFields.DurationMs)

  @JvmStatic
  fun onSourcesAttached(project: Project, handlerClass: Class<*>, language: Language, success: Boolean, durationMs: Long) {
    val events: List<EventPair<*>> = listOf(
      HANDLER_FIELD.with(handlerClass),
      EventFields.Language.with(language),
      SUCCESS_FIELD.with(success),
      EventFields.DurationMs.with(durationMs)
    )
    SOURCES_ATTACHED_EVENT.log(project, events)
  }
}