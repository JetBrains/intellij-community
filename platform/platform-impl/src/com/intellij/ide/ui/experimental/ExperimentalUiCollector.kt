// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ExperimentalUiCollector : CounterUsagesCollector() {

  enum class SwitchSource {
    ENABLE_NEW_UI_ACTION,
    DISABLE_NEW_UI_ACTION,
    WELCOME_PROMO,
    WHATS_NEW_PAGE
  }

  enum class MeetNewUiAction {
    NEW_UI_LINK,
    DENSITY_CLEAN,
    DENSITY_COMPACT
  }

  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("experimental.ui.interactions", 4)

    private val switchSourceField = EventFields.Enum<SwitchSource>("switch_source")
    private val expUiField = EventFields.Boolean("exp_ui")
    private val switchUi = GROUP.registerVarargEvent("switch.ui", switchSourceField, expUiField)

    @JvmStatic
    fun logSwitchUi(switchSource: SwitchSource, value: Boolean) = switchUi.log(
      switchSourceField with switchSource,
      expUiField with value)

    private val meetNewUiActionField = EventFields.Enum<MeetNewUiAction>("action")
    private val meetNewUiAction = GROUP.registerVarargEvent("meet.new.ui.action", meetNewUiActionField)

    @JvmStatic
    fun logMeetNewUiAction(action: MeetNewUiAction) = meetNewUiAction.log(
      meetNewUiActionField with action)

    private val meetNewUiThemeField = EventFields.StringValidatedByEnum("theme_name", "look_and_feel")
    private val meetNewUiSwitchTheme = GROUP.registerVarargEvent("meet.new.ui.switch_theme", meetNewUiThemeField)

    @JvmStatic
    fun logMeetNewUiTheme(theme: String) = meetNewUiSwitchTheme.log(
      meetNewUiThemeField with theme)
  }

}