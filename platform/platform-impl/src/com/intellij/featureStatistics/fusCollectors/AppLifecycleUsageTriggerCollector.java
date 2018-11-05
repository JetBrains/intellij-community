// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class AppLifecycleUsageTriggerCollector extends ApplicationUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector");

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.lifecycle.app";
  }

  public static void onError(boolean isOOM, boolean isMappingFailed) {
    try {
      String key = "ide.error";
      if (isOOM) {
        key += ".oom";
      }
      if (isMappingFailed) {
        key += ".mappingFailed";
      }
      FUSApplicationUsageTrigger.getInstance().trigger(AppLifecycleUsageTriggerCollector.class, key);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }
}
