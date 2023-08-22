// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Tracker for local statistics (<a href="https://www.jetbrains.com/help/phpstorm/opening-multiple-projects.html">Productivity Guide</a>).
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
 */
public abstract class FeatureUsageTracker {
  public boolean SHOW_IN_COMPILATION_PROGRESS = true;
  public boolean SHOW_IN_OTHER_PROGRESS = true;

  public static FeatureUsageTracker getInstance() {
    return ApplicationManager.getApplication().getService(FeatureUsageTracker.class);
  }

  public abstract void triggerFeatureUsed(@NonNls @NotNull String featureId);

  public abstract void triggerFeatureUsedByAction(@NonNls @NotNull String actionId);

  public abstract void triggerFeatureUsedByIntention(@NotNull Class<?> intentionClass);

  public abstract void triggerFeatureShown(@NonNls String featureId);

  public abstract boolean isToBeShown(@NonNls String featureId, Project project);

  public abstract boolean isToBeAdvertisedInLookup(@NonNls String featureId, Project project);
}
