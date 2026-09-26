// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.actions.CleanupIntention;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CleanupOnScopeIntention extends CleanupIntention {
  public static final CleanupOnScopeIntention INSTANCE = new CleanupOnScopeIntention();

  private CleanupOnScopeIntention() {}

  @Override
  protected @Nullable AnalysisScope getScope(Project project, PsiFile psiFile) {
    Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    AnalysisScope analysisScope = new AnalysisScope(psiFile);
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (psiFile.isPhysical() || virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      analysisScope = new AnalysisScope(project);
    }
    BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(
      CodeInsightBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
      CodeInsightBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")), project, BaseAnalysisActionDialog.standardItems(
      project, analysisScope, module, psiFile),
      AnalysisUIOptions.getInstance(project), true);
    if (!dlg.showAndGet()) {
      return null;
    }
    return dlg.getScope(analysisScope);
  }
}
