
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;

public final class PerformAnalysisInBackgroundOption implements PerformInBackgroundOption {
  private final AnalysisUIOptions myUIOptions;

  public PerformAnalysisInBackgroundOption(Project project) {
    myUIOptions = AnalysisUIOptions.getInstance(project);
  }

  @Override
  public boolean shouldStartInBackground() {
    return myUIOptions.ANALYSIS_IN_BACKGROUND;
  }

  @Override
  public void processSentToBackground() {
    myUIOptions.ANALYSIS_IN_BACKGROUND = true;
  }

}
