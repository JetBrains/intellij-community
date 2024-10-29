// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisActionUtils;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SilentCodeCleanupAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && getInspectionScope(e.getDataContext(), project) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    AnalysisScope analysisScope = getInspectionScope(e.getDataContext(), project);
    if (analysisScope == null) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    runInspections(project, analysisScope);
  }

  @SuppressWarnings("WeakerAccess")
  protected @Nullable Runnable getPostRunnable() { return null; }

  @SuppressWarnings("WeakerAccess")
  protected void runInspections(@NotNull Project project, @NotNull AnalysisScope scope) {
    InspectionProfile profile = getProfileForSilentCleanup(project);
    if (profile == null) {
      return;
    }
    InspectionManager managerEx = InspectionManager.getInstance(project);
    GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase) managerEx.createNewGlobalContext();
    globalContext.codeCleanup(scope, profile, getTemplatePresentation().getText(), getPostRunnable(), false);
  }

  @SuppressWarnings("WeakerAccess")
  protected @Nullable InspectionProfile getProfileForSilentCleanup(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
  }

  @SuppressWarnings("WeakerAccess")
  protected @Nullable AnalysisScope getInspectionScope(@NotNull DataContext dataContext, @NotNull Project project) {
    return AnalysisActionUtils.getInspectionScope(dataContext, project, false);
  }
}
