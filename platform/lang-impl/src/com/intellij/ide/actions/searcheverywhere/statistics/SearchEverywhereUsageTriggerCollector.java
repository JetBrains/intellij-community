// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchEverywhereUsageTriggerCollector {

  // this string will be used as ID for contributors from private
  // plugins that mustn't be sent in statistics
  private static final String NOT_REPORTABLE_CONTRIBUTOR_ID = "nonPublicContributor";

  public static final String DIALOG_OPEN = "dialogOpen";
  public static final String TAB_SWITCHED = "tabSwitched";
  public static final String GROUP_NAVIGATE = "navigateThroughGroups";
  public static final String CONTRIBUTOR_ITEM_SELECTED = "contributorItemChosen";
  public static final String MORE_ITEM_SELECTED = "moreItemChosen";
  public static final String COMMAND_USED = "commandUsed";
  public static final String COMMAND_COMPLETED = "commandCompleted";

  public static void trigger(@NotNull Project project, @NotNull String feature, @Nullable FUSUsageContext context) {
    FUCounterUsageLogger.getInstance().logEvent(project, "searchEverywhere", feature, new FeatureUsageData().addFeatureContext(context));
  }

  @NotNull
  public static FUSUsageContext createContext(@Nullable String contributorID, @Nullable String shortcut) {
    return FUSUsageContext.create(contributorID, shortcut);
  }

  @NotNull
  public static String getReportableContributorID(@NotNull SearchEverywhereContributor<?> contributor) {
    Class<? extends SearchEverywhereContributor> clazz = contributor.getClass();
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    return pluginInfo.isSafeToReport() ? contributor.getSearchProviderId() : NOT_REPORTABLE_CONTRIBUTOR_ID;
  }
}
