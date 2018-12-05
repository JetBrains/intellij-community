// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchEverywhereUsageTriggerCollector extends ProjectUsageTriggerCollector {

  private static final String GROUP_ID = "statistics.searchEverywhere";

  public static final String DIALOG_OPEN = "dialogOpen";
  public static final String TAB_SWITCHED = "tabSwitched";
  public static final String GROUP_NAVIGATE = "navigateThroughGroups";
  public static final String CONTRIBUTOR_ITEM_SELECTED = "contributorItemChosen";
  public static final String MORE_ITEM_SELECTED = "moreItemChosen";
  public static final String COMMAND_USED = "commandUsed";
  public static final String COMMAND_COMPLETED = "commandCompleted";

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP_ID;
  }

  public static void trigger(@NotNull Project project, @NotNull String feature, @Nullable FUSUsageContext context) {
    FUSProjectUsageTrigger.getInstance(project).trigger(SearchEverywhereUsageTriggerCollector.class, feature, context);
  }
}
