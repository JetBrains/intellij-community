// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;

public class SearchEverywhereExperimentStrategy {
  private static final int NUMBER_OF_GROUPS = 8;
  private static final int EXPERIMENT_GROUP = 6;

  private final int myExperimentGroup;
  private final boolean myPerformExperiment;

  public SearchEverywhereExperimentStrategy(int bucket) {
    myExperimentGroup = bucket % NUMBER_OF_GROUPS;
    myPerformExperiment = myExperimentGroup == EXPERIMENT_GROUP &&
      StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP();
  }

  public boolean shouldOrderByMl() {
    if (Registry.is("search.everywhere.sort.actions.by.ml")) return true;
    if (Registry.is("search.everywhere.force.disable.experiment.action.ml")) return false;
    return myPerformExperiment;
  }

  public int getExperimentGroup() {
    return myExperimentGroup;
  }
}
