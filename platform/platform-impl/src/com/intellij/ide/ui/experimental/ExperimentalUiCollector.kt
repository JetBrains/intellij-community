// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ExperimentalUiCollector : CounterUsagesCollector() {

  enum class SwitchSource {
    ENABLE_NEW_UI_ACTION,
    DISABLE_NEW_UI_ACTION,
    SETTINGS,
    WELCOME_PROMO,
    WHATS_NEW_PAGE,
    PREFERENCES
  }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("experimental.ui.interactions", 8)

  @JvmStatic
  val islandsThemeOn: EventId = GROUP.registerEvent("islands.theme.on")

  @JvmStatic
  val islandsThemeOff: EventId = GROUP.registerEvent("islands.theme.off")

}
