// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;

public class TipsOfTheDayUsagesCollector  {

  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("statistics.ui.tips",1);

  public static void trigger(String feature) {
    FeatureUsageLogger.INSTANCE.log(GROUP, feature);
  }
}
