package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenTabUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen.tab", 3)

  private val WELCOME_SCREEN_TAB_OPENED = GROUP.registerEvent("welcome.screen.tab.opened")
  fun logWelcomeScreenTabOpened(): Unit = WELCOME_SCREEN_TAB_OPENED.log()

  private val WELCOME_SCREEN_TAB_CLOSED = GROUP.registerEvent("welcome.screen.tab.closed")
  fun logWelcomeScreenTabClosed(): Unit = WELCOME_SCREEN_TAB_CLOSED.log()

  private val welcomeTabIsEnabled: BooleanEventField = EventFields.Boolean("isEnabled")
  private val WELCOME_SCREEN_TAB_VISIBILITY_CHANGED = GROUP.registerEvent("welcome.screen.tab.visibility.changed", welcomeTabIsEnabled)
  fun logWelcomeScreenTabEnabled(): Unit = WELCOME_SCREEN_TAB_VISIBILITY_CHANGED.log(true)
  fun logWelcomeScreenTabDisabled(): Unit = WELCOME_SCREEN_TAB_VISIBILITY_CHANGED.log(false)

  private val comboBoxKindField: EnumEventField<WelcomeScreenComboBoxKind> = EventFields.Enum("combobox", WelcomeScreenComboBoxKind::class.java)
  private val WELCOME_SCREEN_COMBOBOX_VALUE_CHANGED = GROUP.registerEvent("welcome.screen.tab.combobox.value.changed", comboBoxKindField)
  fun logComboBoxValueChanged(kind: WelcomeScreenComboBoxKind): Unit = WELCOME_SCREEN_COMBOBOX_VALUE_CHANGED.log(kind)

  private val startupReopenLastProjectField: BooleanEventField = EventFields.Boolean("isReopenLastProject")
  private val WELCOME_SCREEN_STARTUP_OPTION_CHANGED = GROUP.registerEvent("welcome.screen.tab.startup.option.changed", startupReopenLastProjectField)
  fun logStartupOptionChanged(isReopenLastProject: Boolean): Unit = WELCOME_SCREEN_STARTUP_OPTION_CHANGED.log(isReopenLastProject)

  override fun getGroup(): EventLogGroup = GROUP
}

internal enum class WelcomeScreenComboBoxKind { THEME, KEYMAP, STARTUP }
