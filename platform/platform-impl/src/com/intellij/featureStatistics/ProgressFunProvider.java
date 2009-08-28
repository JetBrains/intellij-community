package com.intellij.featureStatistics;

import com.intellij.openapi.progress.ProgressFunComponentProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ide.TipOfTheDayManager;
import com.intellij.featureStatistics.ui.ProgressTipPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
*/
public final class ProgressFunProvider implements ProgressFunComponentProvider {
  @Nullable
  public JComponent getProgressFunComponent(Project project, String processId) {
    FeatureUsageTrackerImpl tracker = (FeatureUsageTrackerImpl) FeatureUsageTracker.getInstance();
    if (ProgressFunComponentProvider.COMPILATION_ID.equals(processId)) {
      if (!tracker.SHOW_IN_COMPILATION_PROGRESS) return null;
    }
    else {
      if (!tracker.SHOW_IN_OTHER_PROGRESS) return null;
    }

    String[] features = tracker.getFeaturesToShow(project);
    if (features.length > 0) {
      if (!tracker.HAVE_BEEN_SHOWN) {
        tracker.HAVE_BEEN_SHOWN = true;
        String[] newFeatures = new String[features.length + 1];
        newFeatures[0] = ProductivityFeaturesRegistryImpl.WELCOME;
        System.arraycopy(features, 0, newFeatures, 1, features.length);
        features = newFeatures;
      }
      TipOfTheDayManager.getInstance().doNotShowThisTime();
      return new ProgressTipPanel(features, project).getComponent();
    }
    return null;
  }
}
