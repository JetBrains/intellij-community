// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project


object LogConsoleLogHandlerCollectors : CounterUsagesCollector() {

  private const val CLASS_TYPE = "class"
  private const val LOG_TYPE = "log_call"

  private val ourGroup = EventLogGroup("jvm.console.log.filter", 1)

  private val TYPE_FINDER: EventField<String?> = String("type", listOf(CLASS_TYPE, LOG_TYPE))

  private val NUMBER_ITEMS: IntEventField = Int("number_items")

  private val HANDLE_EVENT = ourGroup.registerVarargEvent("handle",
                                                          TYPE_FINDER,
                                                          NUMBER_ITEMS)

  override fun getGroup(): EventLogGroup {
    return ourGroup
  }

  fun logHandleClass(project: Project?, numberItems: Int) {
    val data = ArrayList<EventPair<*>>(2)

    data.add(TYPE_FINDER.with(CLASS_TYPE))
    data.add(NUMBER_ITEMS.with(numberItems))

    HANDLE_EVENT.log(project, data)
  }

  fun logHandleLogCalls(project: Project?, numberItems: Int) {
    val data = ArrayList<EventPair<*>>(2)

    data.add(TYPE_FINDER.with(LOG_TYPE))
    data.add(NUMBER_ITEMS.with(numberItems))

    HANDLE_EVENT.log(project, data)
  }
}
