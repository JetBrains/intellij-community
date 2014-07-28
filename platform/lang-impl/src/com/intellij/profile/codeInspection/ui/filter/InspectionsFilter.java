/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.filter;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public abstract class InspectionsFilter {

  private final Set<HighlightSeverity> mySuitableSeverities = new HashSet<HighlightSeverity>();
  private Boolean mySuitableInspectionsStates;
  private boolean myAvailableOnlyForAnalyze;

  public boolean isAvailableOnlyForAnalyze() {
    return myAvailableOnlyForAnalyze;
  }

  public Boolean getSuitableInspectionsStates() {
    return mySuitableInspectionsStates;
  }

  public boolean containsSeverity(final HighlightSeverity severity) {
    return mySuitableSeverities.contains(severity);
  }

  public void setAvailableOnlyForAnalyze(final boolean availableOnlyForAnalyze) {
    myAvailableOnlyForAnalyze = availableOnlyForAnalyze;
    filterChanged();
  }

  public void setSuitableInspectionsStates(@Nullable final Boolean suitableInspectionsStates) {
    mySuitableInspectionsStates = suitableInspectionsStates;
    filterChanged();
  }

  public void add(final HighlightSeverity severity) {
    mySuitableSeverities.add(severity);
    filterChanged();
  }

  public void remove(final HighlightSeverity severity) {
    mySuitableSeverities.remove(severity);
    filterChanged();
  }

  public boolean isEmptyFilter() {
    return mySuitableInspectionsStates == null && !myAvailableOnlyForAnalyze && mySuitableSeverities.isEmpty();
  }

  public boolean matches(final Tools tools) {
    if (mySuitableInspectionsStates != null && mySuitableInspectionsStates != tools.isEnabled()) {
      return false;
    }

    if (myAvailableOnlyForAnalyze != isAvailableOnlyForAnalyze(tools)) {
      return false;
    }

    if (mySuitableSeverities.isEmpty()) {
      return true;
    }
    for (final ScopeToolState state : tools.getTools()) {
      if (mySuitableInspectionsStates != null && mySuitableInspectionsStates != state.isEnabled()) {
        continue;
      }
      if (mySuitableSeverities.contains(tools.getDefaultState().getLevel().getSeverity())) {
        return true;
      }
    }
    return false;
  }

  protected abstract void filterChanged();

  private static boolean isAvailableOnlyForAnalyze(final Tools tools) {
    final InspectionToolWrapper tool = tools.getTool();
    return tool instanceof GlobalInspectionToolWrapper && ((GlobalInspectionToolWrapper)tool).worksInBatchModeOnly();
  }
}