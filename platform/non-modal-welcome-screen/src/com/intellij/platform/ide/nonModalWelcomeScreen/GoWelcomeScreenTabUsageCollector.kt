package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object GoWelcomeScreenTabUsageCollector : CounterUsagesCollector() {
  enum class Feature {
    Terminal,
    Docker,
    Kubernetes,
    HttpClient,
    Database,
    Plugins
  }

  private val GROUP = EventLogGroup("go.welcome.screen.tab", 2)

  private val WELCOME_SCREEN_TAB_OPENED = GROUP.registerEvent("welcome.screen.tab.opened")
  fun logWelcomeScreenTabOpened(): Unit = WELCOME_SCREEN_TAB_OPENED.log()

  private val WELCOME_SCREEN_TAB_CLOSED = GROUP.registerEvent("welcome.screen.tab.closed")
  fun logWelcomeScreenTabClosed(): Unit = WELCOME_SCREEN_TAB_CLOSED.log()

  private val WELCOME_SCREEN_TAB_DISABLED = GROUP.registerEvent("welcome.screen.tab.disabled")
  fun logWelcomeScreenTabDisabled(): Unit = WELCOME_SCREEN_TAB_DISABLED.log()

  private val feature: EnumEventField<Feature> = EventFields.Enum("feature", Feature::class.java)
  private val WELCOME_SCREEN_TAB_BUTTON_CLICKED = GROUP.registerEvent("welcome.screen.tab.button.clicked", feature)
  fun logWelcomeScreenTabButtonClicked(feature: Feature): Unit = WELCOME_SCREEN_TAB_BUTTON_CLICKED.log(feature)

  override fun getGroup(): EventLogGroup = GROUP
}
