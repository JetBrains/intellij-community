// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class FeatureUsageTracker {
  public boolean SHOW_IN_COMPILATION_PROGRESS = true;
  public boolean SHOW_IN_OTHER_PROGRESS = true;

  public static FeatureUsageTracker getInstance() {
    return ApplicationManager.getApplication().getService(FeatureUsageTracker.class);
  }

  public abstract void triggerFeatureUsed(@NonNls @NotNull String featureId);

  public abstract void triggerFeatureShown(@NonNls String featureId);

  public abstract boolean isToBeShown(@NonNls String featureId, Project project);

  public abstract boolean isToBeAdvertisedInLookup(@NonNls String featureId, Project project);
}
