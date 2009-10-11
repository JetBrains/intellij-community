/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
