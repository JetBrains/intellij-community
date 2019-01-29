// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;

public class TipsOfTheDayUsagesCollector  {
  private static final String GROUP = "statistics.ui.tips";

  public static void trigger(String feature) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP, feature);
  }
}
