package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;

/**
 * User: anna
 * Date: Jan 28, 2005
 */
public abstract class FeatureUsageTracker {
  public boolean SHOW_IN_COMPILATION_PROGRESS = true;
  public boolean SHOW_IN_OTHER_PROGRESS = true;

  public static FeatureUsageTracker getInstance() {
    return ApplicationManager.getApplication().getComponent(FeatureUsageTracker.class);
  }

  public abstract void triggerFeatureUsed(String featureId);

  public abstract void triggerFeatureShown(String featureId);
}
