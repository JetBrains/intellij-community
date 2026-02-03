// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector


internal object RainbowCollector : CounterUsagesCollector() {
  @JvmStatic
  private val RAINBOW_GROUP = EventLogGroup("rainbow.highlighter", 2)

  @JvmStatic
  val RAINBOW_HIGHLIGHTER_CHANGED_EVENT = RAINBOW_GROUP.registerEvent(
    "rainbow.highlighter.changed",
    EventFields.Boolean("rainbowOnByDefault"),
    EventFields.StringListValidatedByCustomRule("rainbowOnLanguageIDs", LangCustomRuleValidator::class.java)
  )

  override fun getGroup(): EventLogGroup = RAINBOW_GROUP
}
