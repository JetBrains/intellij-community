// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

public class AppLifecycleUsageTriggerCollector {
  public static final FeatureUsageGroup LIFECYCLE = new FeatureUsageGroup("lifecycle", 1);
  public static final FeatureUsageGroup LIFECYCLE_APP = new FeatureUsageGroup("statistics.lifecycle.app", 1);
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector");

  public static void onError(boolean isOOM, boolean isMappingFailed, @Nullable String pluginId) {
    try {
      FeatureUsageLogger.INSTANCE
        .log(LIFECYCLE_APP, "ide.error", StatisticsUtilKt.createData(null, FUSUsageContext.create("OOM:" + isOOM,
                                                                                                               "mappingFailed:" +
                                                                                                               isMappingFailed,
                                                                                                               "pluginId:" +
                                                                                                               StringUtil
                                                                                                                 .notNullize(pluginId,
                                                                                                                             "unknown"))));
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }
}
