package com.intellij.ide.customize;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.intellij.ide.customize.CustomizeIDEWizardInteractionType.*;

public class CustomizeWizardCollector extends CounterUsagesCollector {
  public static final EventLogGroup GROUP = new EventLogGroup("customize.wizard", 2);

  private static final IntEventField PAGE_FIELD = EventFields.Int("page");
  private static final LongEventField TIMESTAMP_FIELD = EventFields.Long("timestamp");
  private static final StringEventField GROUP_FIELD =
    EventFields.String("group", Arrays.asList("Java_Frameworks", "Web_Development", "Version_Controls", "Test_Tools", "Application_Servers",
                                              "Clouds", "Swing", "Android", "Database_Tools", "Other_Tools", "Plugin_Development",
                                              "Build_Tools"));

  public static final EventId1<Integer> REMAINING_PAGES_SKIPPED_EVENT = GROUP.registerEvent("remaining.pages.skipped", PAGE_FIELD);
  public static final VarargEventId WIZARD_DISPLAYED_EVENT =
    GROUP.registerVarargEvent(WizardDisplayed.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId BUNDLED_PLUGIN_GROUP_ENABLED_EVENT =
    GROUP.registerVarargEvent(BundledPluginGroupEnabled.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId BUNDLED_PLUGIN_GROUP_CUSTOMIZED_EVENT =
    GROUP.registerVarargEvent(BundledPluginGroupCustomized.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId BUNDLED_PLUGIN_GROUP_DISABLED_EVENT =
    GROUP.registerVarargEvent(BundledPluginGroupDisabled.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId DESKTOP_ENTRY_CREATED_EVENT =
    GROUP.registerVarargEvent(DesktopEntryCreated.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId LAUNCHER_SCRIPT_CREATED_EVENT =
    GROUP.registerVarargEvent(LauncherScriptCreated.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId FEATURED_PLUGIN_INSTALLED_EVENT =
    GROUP.registerVarargEvent(FeaturedPluginInstalled.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);
  public static final VarargEventId UI_THEME_CHANGED_EVENT =
    GROUP.registerVarargEvent(UIThemeChanged.toString(), TIMESTAMP_FIELD, EventFields.PluginInfo, GROUP_FIELD);

  private static final HashMap<CustomizeIDEWizardInteractionType, VarargEventId> eventIdHashMap = new HashMap<>();

  static {
    eventIdHashMap.put(WizardDisplayed, WIZARD_DISPLAYED_EVENT);
    eventIdHashMap.put(BundledPluginGroupEnabled, BUNDLED_PLUGIN_GROUP_ENABLED_EVENT);
    eventIdHashMap.put(BundledPluginGroupCustomized, BUNDLED_PLUGIN_GROUP_CUSTOMIZED_EVENT);
    eventIdHashMap.put(BundledPluginGroupDisabled, BUNDLED_PLUGIN_GROUP_DISABLED_EVENT);
    eventIdHashMap.put(DesktopEntryCreated, DESKTOP_ENTRY_CREATED_EVENT);
    eventIdHashMap.put(LauncherScriptCreated, LAUNCHER_SCRIPT_CREATED_EVENT);
    eventIdHashMap.put(FeaturedPluginInstalled, FEATURED_PLUGIN_INSTALLED_EVENT);
    eventIdHashMap.put(UIThemeChanged, UI_THEME_CHANGED_EVENT);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logRemainingPagesSkipped(int page) {
    REMAINING_PAGES_SKIPPED_EVENT.log(page);
  }

  public static void logEvent(String eventId, long timestamp, PluginInfo pluginInfo, String group) throws Exception {
    VarargEventId event = eventIdHashMap.get(CustomizeIDEWizardInteractionType.valueOf(eventId));
    if (event == null) {
      throw new IllegalStateException(String.format("Not found event by eventId %s", eventId));
    }

    List<EventPair<?>> data = new ArrayList<>();
    data.add(TIMESTAMP_FIELD.with(timestamp));
    if (pluginInfo != null) {
      data.add(EventFields.PluginInfo.with(pluginInfo));
    }
    if (group != null) {
      data.add(GROUP_FIELD.with(group));
    }

    event.log(data);
  }
}