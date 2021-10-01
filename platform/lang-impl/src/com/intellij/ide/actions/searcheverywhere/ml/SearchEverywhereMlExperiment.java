// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;

import static com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.RECORDER_CODE;

public class SearchEverywhereMlExperiment {
  private static final int NUMBER_OF_GROUPS = 8;
  private static final int EXPERIMENT_GROUP = 6;

  private final int myExperimentGroup;
  private final boolean myPerformExperiment;

  private final boolean myIsExperimentalMode;

  public SearchEverywhereMlExperiment() {
    myIsExperimentalMode = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP();
    myExperimentGroup = myIsExperimentalMode ? EventLogConfiguration.getInstance().getOrCreate(RECORDER_CODE).getBucket() % NUMBER_OF_GROUPS : -1;
    myPerformExperiment = myExperimentGroup == EXPERIMENT_GROUP;
  }

  public boolean isAllowed() {
    if (isOrderByMlEnabled()) return true;
    return !isDisableLoggingAndExperiments();
  }

  public boolean shouldOrderByMl() {
    if (isOrderByMlEnabled()) return true;
    if (isDisableLoggingAndExperiments() || isDisableExperiments()) return false;
    return myPerformExperiment;
  }

  private boolean isDisableLoggingAndExperiments() {
    return !myIsExperimentalMode || Registry.is("search.everywhere.force.disable.logging.ml");
  }

  private static boolean isDisableExperiments() {
    return Registry.is("search.everywhere.force.disable.experiment.action.ml");
  }

  private static boolean isOrderByMlEnabled() {
    return Registry.is("search.everywhere.sort.actions.by.ml");
  }

  public int getExperimentGroup() {
    return myExperimentGroup;
  }
}
