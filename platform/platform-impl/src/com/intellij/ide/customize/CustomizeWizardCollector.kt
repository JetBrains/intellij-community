package com.intellij.ide.customize

import com.intellij.ide.customize.CustomizeIDEWizardInteractionType.*
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo

class CustomizeWizardCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("customize.wizard", 2)
    private val PAGE_FIELD = Int("page")
    private val TIMESTAMP_FIELD = Long("timestamp")
    private val GROUP_FIELD = String("group", listOf("Java_Frameworks", "Web_Development", "Version_Controls", "Test_Tools",
                                                     "Application_Servers", "Clouds", "Swing", "Android", "Database_Tools",
                                                     "Other_Tools", "Plugin_Development", "Build_Tools"))
    private val REMAINING_PAGES_SKIPPED_EVENT = GROUP.registerEvent("remaining.pages.skipped", PAGE_FIELD)

    private val WIZARD_DISPLAYED_EVENT = getEvent(WizardDisplayed.toString())
    private val BUNDLED_PLUGIN_GROUP_ENABLED_EVENT = getEvent(BundledPluginGroupEnabled.toString())
    private val BUNDLED_PLUGIN_GROUP_CUSTOMIZED_EVENT = getEvent(BundledPluginGroupCustomized.toString())
    private val BUNDLED_PLUGIN_GROUP_DISABLED_EVENT = getEvent(BundledPluginGroupDisabled.toString())
    private val DESKTOP_ENTRY_CREATED_EVENT = getEvent(DesktopEntryCreated.toString())
    private val LAUNCHER_SCRIPT_CREATED_EVENT = getEvent(LauncherScriptCreated.toString())
    private val FEATURED_PLUGIN_INSTALLED_EVENT = getEvent(FeaturedPluginInstalled.toString())
    private val UI_THEME_CHANGED_EVENT = getEvent(UIThemeChanged.toString())

    fun getEvent(eventId: String): VarargEventId {
      return ActionsEventLogGroup.registerActionEvent(GROUP, eventId, TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD)
    }

    fun logRemainingPagesSkipped(page: Int) {
      REMAINING_PAGES_SKIPPED_EVENT.log(page)
    }

    fun logEvent(eventId: CustomizeIDEWizardInteractionType, timestamp: Long, pluginInfo: PluginInfo?, group: String?) {
      val data: MutableList<EventPair<*>> = ArrayList()
      data.add(TIMESTAMP_FIELD.with(timestamp))
      if (pluginInfo != null) {
        data.add(EventFields.PluginInfo.with(pluginInfo))
      }
      if (group != null) {
        data.add(GROUP_FIELD.with(group))
      }
      when (eventId) {
        WizardDisplayed -> WIZARD_DISPLAYED_EVENT.log(data)
        UIThemeChanged -> UI_THEME_CHANGED_EVENT.log(data)
        DesktopEntryCreated -> DESKTOP_ENTRY_CREATED_EVENT.log(data)
        LauncherScriptCreated -> LAUNCHER_SCRIPT_CREATED_EVENT.log(data)
        BundledPluginGroupDisabled -> BUNDLED_PLUGIN_GROUP_DISABLED_EVENT.log(data)
        BundledPluginGroupEnabled -> BUNDLED_PLUGIN_GROUP_ENABLED_EVENT.log(data)
        BundledPluginGroupCustomized -> BUNDLED_PLUGIN_GROUP_CUSTOMIZED_EVENT.log(data)
        FeaturedPluginInstalled -> FEATURED_PLUGIN_INSTALLED_EVENT.log(data)
      }
    }
  }
}