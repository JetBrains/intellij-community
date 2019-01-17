// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;

public class ProjectLifecycleUsageTriggerCollector {
  public static final FeatureUsageGroup GROUP_ID = new FeatureUsageGroup("statistics.lifecycle.project",1);
}
