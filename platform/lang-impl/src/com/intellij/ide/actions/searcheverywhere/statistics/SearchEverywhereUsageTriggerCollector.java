// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class SearchEverywhereUsageTriggerCollector extends CounterUsagesCollector {

  // this string will be used as ID for contributors from private
  // plugins that mustn't be sent in statistics
  private static final String NOT_REPORTABLE_CONTRIBUTOR_ID = "third.party";

  public static final StringEventField CONTRIBUTOR_ID_FIELD = EventFields.String("contributorID",
                                                                                 Arrays.asList("FileSearchEverywhereContributor",
                                                                                               "SearchEverywhereContributor.All",
                                                                                               "ClassSearchEverywhereContributor",
                                                                                               "RecentFilesSEContributor",
                                                                                               "ActionSearchEverywhereContributor",
                                                                                               "SymbolSearchEverywhereContributor",
                                                                                               "TopHitSEContributor",
                                                                                               "RunConfigurationsSEContributor",
                                                                                               "YAMLKeysSearchEverywhereContributor",
                                                                                               "CommandsContributor", "third.party",
                                                                                               "Vcs.Git", "UrlSearchEverywhereContributor",
                                                                                               "GitSearchEverywhereContributor",
                                                                                               "TextSearchContributor"));

  public static final StringEventField CURRENT_TAB_FIELD = EventFields.String("currentTabId",
                                                                              Arrays.asList("FileSearchEverywhereContributor",
                                                                                            "SearchEverywhereContributor.All",
                                                                                            "ClassSearchEverywhereContributor",
                                                                                            "ActionSearchEverywhereContributor",
                                                                                            "SymbolSearchEverywhereContributor",
                                                                                            "third.party", "Vcs.Git"));
  private static final EventLogGroup GROUP = new EventLogGroup("searchEverywhere", 7);

  public static final EventId2<String, AnActionEvent> DIALOG_OPEN = GROUP.registerEvent("dialogOpen",
                                                                                        CONTRIBUTOR_ID_FIELD,
                                                                                        EventFields.InputEventByAnAction);
  public static final VarargEventId TAB_SWITCHED = GROUP.registerVarargEvent("tabSwitched", CONTRIBUTOR_ID_FIELD,
                                                                             EventFields.InputEventByAnAction,
                                                                             EventFields.InputEventByMouseEvent);
  public static final EventId1<AnActionEvent> GROUP_NAVIGATE = GROUP.registerEvent("navigateThroughGroups",
                                                                                   EventFields.InputEventByAnAction);
  public static final EventId DIALOG_CLOSED = GROUP.registerEvent("dialogClosed");
  public static final IntEventField SELECTED_ITEM_NUMBER = EventFields.Int("selectedItemNumber");

  public static final VarargEventId CONTRIBUTOR_ITEM_SELECTED = GROUP.registerVarargEvent("contributorItemChosen", CONTRIBUTOR_ID_FIELD,
                                                                                          EventFields.Language, CURRENT_TAB_FIELD,
                                                                                          SELECTED_ITEM_NUMBER);
  public static final EventId MORE_ITEM_SELECTED = GROUP.registerEvent("moreItemChosen");
  public static final EventId COMMAND_USED = GROUP.registerEvent("commandUsed");

  public static final VarargEventId COMMAND_COMPLETED = GROUP.registerVarargEvent("commandCompleted",
                                                                                  EventFields.InputEventByAnAction);
  public static final IntEventField TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys");
  public static final IntEventField TYPED_NAVIGATION_KEYS = EventFields.Int("typedNavigationKeys");
  public static final EventId2<Integer, Integer> SESSION_FINISHED =
    GROUP.registerEvent("sessionFinished", TYPED_NAVIGATION_KEYS, TYPED_SYMBOL_KEYS);

  public static final String TYPED_BACKSPACES_DATA_KEY = "typedBackspaces";
  public static final String SESSION_ID_LOG_DATA_KEY = "sessionId";
  public static final String COLLECTED_RESULTS_DATA_KEY = "collectedResults";
  public static final String SELECTED_INDEXES_DATA_KEY = "selectedIndexes";
  public static final String TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount";

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  public static String getReportableContributorID(@NotNull SearchEverywhereContributor<?> contributor) {
    Class<? extends SearchEverywhereContributor> clazz = contributor.getClass();
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    return pluginInfo.isDevelopedByJetBrains() ? contributor.getSearchProviderId() : NOT_REPORTABLE_CONTRIBUTOR_ID;
  }
}
