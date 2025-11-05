package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenTabUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen.tab", 2)

  private val WELCOME_SCREEN_TAB_OPENED = GROUP.registerEvent("welcome.screen.tab.opened")
  fun logWelcomeScreenTabOpened(): Unit = WELCOME_SCREEN_TAB_OPENED.log()

  private val WELCOME_SCREEN_TAB_CLOSED = GROUP.registerEvent("welcome.screen.tab.closed")
  fun logWelcomeScreenTabClosed(): Unit = WELCOME_SCREEN_TAB_CLOSED.log()

  private val welcomeTabIsEnabled: BooleanEventField = EventFields.Boolean("isEnabled")
  private val WELCOME_SCREEN_TAB_VISIBILITY_CHANGED = GROUP.registerEvent("welcome.screen.tab.visibility.changed", welcomeTabIsEnabled,
                                                                          description = "Event logged when the visibility of the welcome screen tab is changed by 'Show this page on startup' checkbox. Also could be changed via Advanced settings, but will be logged only if the current project is welcome screen.")
  fun logWelcomeScreenTabEnabled(): Unit = WELCOME_SCREEN_TAB_VISIBILITY_CHANGED.log(true)
  fun logWelcomeScreenTabDisabled(): Unit = WELCOME_SCREEN_TAB_VISIBILITY_CHANGED.log(false)

  override fun getGroup(): EventLogGroup = GROUP
}
