// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo

internal object InlayActionHandlerUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("inlay.action.handler", 1)
  private val HANDLER_FIELD = EventFields.StringValidatedByCustomRule("id", PluginInfoValidationRule::class.java)
  private val CLICK_HANDLER_EVENT = GROUP.registerEvent("click.handled",
                                                        HANDLER_FIELD,
                                                        EventFields.PluginInfo)

  fun clickHandled(handlerId: String, handlerClass: Class<*>) {
    CLICK_HANDLER_EVENT.log(handlerId, getPluginInfo(handlerClass))
  }
}