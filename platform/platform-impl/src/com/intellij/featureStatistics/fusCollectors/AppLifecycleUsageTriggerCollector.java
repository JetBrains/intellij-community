// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AppLifecycleUsageTriggerCollector extends ApplicationUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector");

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.lifecycle.app";
  }

  public static void onError(boolean isOOM, boolean isMappingFailed, @Nullable String pluginId) {
    try {
      FUSApplicationUsageTrigger.getInstance().trigger(AppLifecycleUsageTriggerCollector.class, "ide.error",
                                                       FUSUsageContext.create("OOM:" + isOOM,
                                                                              "mappingFailed:" + isMappingFailed,
                                                                              "pluginId:" + StringUtil.notNullize(pluginId, "unknown")));
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }
}
