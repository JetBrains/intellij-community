// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AccessibilityUsageTrackerCollector extends CounterUsagesCollector {

  private static final Collection<EventId> ourRaisedEvents = new ConcurrentLinkedQueue<>();

  private static final EventLogGroup GROUP = new EventLogGroup("accessibility", 1);
  public static final EventId SCREEN_READER_DETECTED = GROUP.registerEvent("screen.reader.detected");
  public static final EventId SCREEN_READER_SUPPORT_ENABLED = GROUP.registerEvent("screen.reader.support.enabled");
  public static final EventId SCREEN_READER_SUPPORT_ENABLED_VM = GROUP.registerEvent("screen.reader.support.enabled.in.vmoptions");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void featureTriggered(EventId feature) {
    ourRaisedEvents.add(feature);
  }

  private static void saveStatistics() {
    ourRaisedEvents.forEach(EventId::log);
  }

  public static class CollectStatisticsTask implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
      saveStatistics();
    }
  }
}
