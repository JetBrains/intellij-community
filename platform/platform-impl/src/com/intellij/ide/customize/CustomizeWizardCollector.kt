package com.intellij.ide.customize

import com.intellij.ide.customize.CustomizeIDEWizardInteractionType.*
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo

class CustomizeWizardCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("customize.wizard", 2)
    private val PAGE_FIELD = EventFields.Int("page")
    private val TIMESTAMP_FIELD = EventFields.Long("timestamp")
    private val GROUP_FIELD = EventFields.String("group", listOf("Java_Frameworks", "Web_Development", "Version_Controls", "Test_Tools",
                                                                 "Application_Servers", "Clouds", "Swing", "Android", "Database_Tools",
                                                                 "Other_Tools", "Plugin_Development", "Build_Tools"))
    private val REMAINING_PAGES_SKIPPED_EVENT = GROUP.registerEvent("remaining.pages.skipped", PAGE_FIELD)

    private val WIZARD_DISPLAYED_EVENT = registerEvent(WizardDisplayed)
    private val BUNDLED_PLUGIN_GROUP_ENABLED_EVENT = registerEvent(BundledPluginGroupEnabled)
    private val BUNDLED_PLUGIN_GROUP_CUSTOMIZED_EVENT = registerEvent(BundledPluginGroupCustomized)
    private val BUNDLED_PLUGIN_GROUP_DISABLED_EVENT = registerEvent(BundledPluginGroupDisabled)
    private val DESKTOP_ENTRY_CREATED_EVENT = registerEvent(DesktopEntryCreated)
    private val LAUNCHER_SCRIPT_CREATED_EVENT = registerEvent(LauncherScriptCreated)
    private val FEATURED_PLUGIN_INSTALLED_EVENT = registerEvent(FeaturedPluginInstalled)
    private val UI_THEME_CHANGED_EVENT = registerEvent(UIThemeChanged)

    fun registerEvent(customizeIDEWizardInteractionType: CustomizeIDEWizardInteractionType): VarargEventId {
      return GROUP.registerVarargEvent(customizeIDEWizardInteractionType.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD)
    }

    fun logRemainingPagesSkipped(page: Int) {
      REMAINING_PAGES_SKIPPED_EVENT.log(page)
    }

    fun logEvent(eventId: CustomizeIDEWizardInteractionType, timestamp: Long, pluginInfo: PluginInfo?, group: String?) {
      val data = mutableListOf<EventPair<*>>()
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