package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenTabUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen.tab", 1)

  private val WELCOME_SCREEN_TAB_OPENED = GROUP.registerEvent("welcome.screen.tab.opened")
  fun logWelcomeScreenTabOpened(): Unit = WELCOME_SCREEN_TAB_OPENED.log()

  private val WELCOME_SCREEN_TAB_CLOSED = GROUP.registerEvent("welcome.screen.tab.closed")
  fun logWelcomeScreenTabClosed(): Unit = WELCOME_SCREEN_TAB_CLOSED.log()

  private val WELCOME_SCREEN_TAB_DISABLED = GROUP.registerEvent("welcome.screen.tab.disabled")
  fun logWelcomeScreenTabDisabled(): Unit = WELCOME_SCREEN_TAB_DISABLED.log()

  override fun getGroup(): EventLogGroup = GROUP
}
