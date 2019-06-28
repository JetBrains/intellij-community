// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import org.jetbrains.annotations.NotNull;

public class TipsOfTheDayUsagesCollector  {

  public static void trigger(String feature) {
    FUCounterUsageLogger.getInstance().logEvent("ui.tips", feature);
  }

  public static void triggerShow(@NotNull String type) {
    FUCounterUsageLogger.getInstance().logEvent("ui.tips", "dialog.shown", new FeatureUsageData().addData("type", type));
  }
}
