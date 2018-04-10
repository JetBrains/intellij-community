/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisActionUtils;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SilentCodeCleanupAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !DumbService.isDumb(project) && getInspectionScope(e.getDataContext(), project) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    AnalysisScope analysisScope = getInspectionScope(e.getDataContext(), project);
    if (analysisScope == null)
      return;

    FileDocumentManager.getInstance().saveAllDocuments();

    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassist.inspect.batch");
    runInspections(project, analysisScope);
  }

  @SuppressWarnings("WeakerAccess")
  protected void runInspections(@NotNull Project project, @NotNull AnalysisScope scope) {
    InspectionProfile profile = getProfileForSilentCleanup(project);
    if (profile == null) {
      return;
    }
    InspectionManager managerEx = InspectionManager.getInstance(project);
    GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase) managerEx.createNewGlobalContext(false);
    globalContext.codeCleanup(scope, profile, getTemplatePresentation().getText(), null, false);
  }

  @SuppressWarnings("WeakerAccess")
  @Nullable
  protected InspectionProfile getProfileForSilentCleanup(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
  }

  @Nullable
  @SuppressWarnings("WeakerAccess")
  protected AnalysisScope getInspectionScope(@NotNull DataContext dataContext, @NotNull Project project) {
    return AnalysisActionUtils.getInspectionScope(dataContext, project, false);
  }
}
