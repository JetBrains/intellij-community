// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language


internal object RainbowCollector : CounterUsagesCollector() {
  @JvmStatic
  private val RAINBOW_GROUP = EventLogGroup("rainbow.highlighter", 1)
  private val KNOWN_LANGUAGE_IDS = Language
    .getRegisteredLanguages()
    .map { it.id } // we have an empty string in the list as the ANY language representation
    .plus(RainbowColorsInSchemeState.DEFAULT_LANGUAGE_NAME)
    .sorted()

  @JvmStatic
  val RAINBOW_HIGHLIGHTER_CHANGED_EVENT = RAINBOW_GROUP.registerEvent(
    "rainbow.highlighter.changed",
    EventFields.StringList("rainbowOnLanguageIDs", KNOWN_LANGUAGE_IDS),
  )

  override fun getGroup(): EventLogGroup = RAINBOW_GROUP
}
