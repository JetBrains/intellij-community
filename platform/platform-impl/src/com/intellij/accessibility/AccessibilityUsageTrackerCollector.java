// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AccessibilityUsageTrackerCollector {

  private static final Collection<String> ourRaisedEvents = new ConcurrentLinkedQueue<>();

  private static final String GROUP_ID = "accessibility";
  public static final String SCREEN_READER_DETECTED = "screen.reader.detected";
  public static final String SCREEN_READER_SUPPORT_ENABLED = "screen.reader.support.enabled";
  public static final String SCREEN_READER_SUPPORT_ENABLED_VM = "screen.reader.support.enabled.in.vmoptions";

  public static void featureTriggered(String featureID) {
    ourRaisedEvents.add(featureID);
  }

  private static void saveStatistics() {
    for (String featureID: ourRaisedEvents) {
      FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, featureID);
    }
  }

  public static class CollectStatisticsTask implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
      saveStatistics();
    }
  }
}
