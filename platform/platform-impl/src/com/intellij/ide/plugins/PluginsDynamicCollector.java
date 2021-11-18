package com.intellij.ide.plugins;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import org.jetbrains.annotations.NotNull;

public class PluginsDynamicCollector extends CounterUsagesCollector {
  public static final EventLogGroup GROUP = new EventLogGroup("plugins.dynamic", 1);
  public static final EventId1<PluginInfo> LOAD_EVENT = GROUP.registerEvent("load", EventFields.PluginInfo);
  public static final EventId1<PluginInfo> UNLOAD_SUCCESS_EVENT = GROUP.registerEvent("unload.success", EventFields.PluginInfo);
  public static final EventId1<PluginInfo> UNLOAD_FAIL_EVENT = GROUP.registerEvent("unload.fail", EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void log(@NotNull String eventId, @NotNull PluginInfo pluginInfo) {
    switch (eventId) {
      case "load":
        LOAD_EVENT.log(pluginInfo);
        break;
      case "unload.success":
        UNLOAD_SUCCESS_EVENT.log(pluginInfo);
        break;
      case "unload.fail":
        UNLOAD_FAIL_EVENT.log(pluginInfo);
        break;
    }
  }
}
