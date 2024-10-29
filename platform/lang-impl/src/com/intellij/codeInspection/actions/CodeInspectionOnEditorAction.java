// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CodeInspectionOnEditorAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      return;
    }
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile != null){
      analyze(project, psiFile);
    }
  }

  private static void analyze(Project project, PsiFile psiFile) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final AnalysisScope scope = new AnalysisScope(psiFile);
    final GlobalInspectionContextImpl inspectionContext = inspectionManagerEx.createNewGlobalContext();
    inspectionContext.setCurrentScope(scope);
    inspectionContext.setExternalProfile(InspectionProjectProfileManager.getInstance(project).getCurrentProfile());
    inspectionContext.doInspections(scope);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    e.getPresentation().setEnabled(project != null && psiFile != null  && DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(psiFile));
  }
}
