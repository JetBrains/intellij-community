// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchEverywhereUsageTriggerCollector {

  private static final FeatureUsageGroup GROUP_ID = new FeatureUsageGroup("statistics.searchEverywhere",1);

  public static final String DIALOG_OPEN = "dialogOpen";
  public static final String TAB_SWITCHED = "tabSwitched";
  public static final String GROUP_NAVIGATE = "navigateThroughGroups";
  public static final String CONTRIBUTOR_ITEM_SELECTED = "contributorItemChosen";
  public static final String MORE_ITEM_SELECTED = "moreItemChosen";
  public static final String COMMAND_USED = "commandUsed";
  public static final String COMMAND_COMPLETED = "commandCompleted";

  public static void trigger(@NotNull Project project, @NotNull String feature, @Nullable FUSUsageContext context) {
    FeatureUsageLogger.INSTANCE.log(GROUP_ID, feature, StatisticsUtilKt.createData(project, context));
  }
}
