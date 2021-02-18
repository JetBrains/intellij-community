// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereLogger;
import com.intellij.internal.statistic.local.ActionGlobalUsageInfo;
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GotoActionOrderStrategy {
  private static final int NUMBER_OF_GROUPS = 8;
  private static final int EXPERIMENT_GROUP = 7;

  private final boolean myExperimentGroup;

  private final ActionManager myActionManager = ActionManager.getInstance();
  private final ActionsGlobalSummaryManager myStatManager =
    ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager.class);

  public GotoActionOrderStrategy() {
    myExperimentGroup =
      SearchEverywhereLogger.getBucket() % NUMBER_OF_GROUPS == EXPERIMENT_GROUP &&
      StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP();
  }

  public int compare(@NotNull AnAction first, @NotNull AnAction second) {
    if (shouldOrderByStats()) {
      double myRatio = getActionUsagesRatio(myActionManager.getId(first));
      double oRatio = getActionUsagesRatio(myActionManager.getId(second));
      return -Double.compare(myRatio, oRatio);
    }
    return 0;
  }

  private boolean shouldOrderByStats() {
    if (Registry.is("search.everywhere.consider.action.statistics")) return true;
    if (Registry.is("search.everywhere.force.disable.experiment.action.statistics")) return false;
    return myExperimentGroup;
  }

  private double getActionUsagesRatio(@Nullable String actionID) {
    if (actionID == null) return .0;
    ActionGlobalUsageInfo statistics = myStatManager.getActionStatistics(actionID);
    return statistics != null ? statistics.getUsagesPerUserRatio() : .0;
  }
}
