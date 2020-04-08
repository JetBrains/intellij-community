// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import org.jetbrains.annotations.NotNull;

public class AccessibilityUsageTrackerCollector {
  private static final String FUS_GROUP_ID  = "accessibility";

  /**
   * Enabled screen reader is detected in OS
   */
  public static final String SCREEN_READER_DETECTED = "screenReaderDetected";

  /**
   * After screen reader detection user agreed to enable screen reader support
   */
  public static final String SCREEN_READER_SUPPORT_ENABLED = "screenReaderSupportEnabled";

  public static void trigger(@NotNull String feature) {
    FUCounterUsageLogger.getInstance().logEvent(FUS_GROUP_ID, feature);
  }

}
