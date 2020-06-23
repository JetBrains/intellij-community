// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import com.intellij.internal.statistic.eventLog.EventId;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class AccessibilityUsageTrackerCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("accessibility", 1);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
  /**
   * Enabled screen reader is detected in OS
   */
  public static final EventId SCREEN_READER_DETECTED = GROUP.registerEvent("screenReaderDetected");

  /**
   * After screen reader detection user agreed to enable screen reader support
   */
  public static final EventId SCREEN_READER_SUPPORT_ENABLED = GROUP.registerEvent("screenReaderSupportEnabled");
}
