// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class CleanupIntention implements IntentionAction, LowPriorityAction {

  protected CleanupIntention() {}

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("cleanup.in.scope");
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    final InspectionManager managerEx = InspectionManager.getInstance(project);
    final GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)managerEx.createNewGlobalContext();
    final AnalysisScope scope = getScope(project, InjectedLanguageManager.getInstance(project).getTopLevelFile(file));
    if (scope != null) {
      globalContext.codeCleanup(scope, InspectionProjectProfileManager.getInstance(project).getCurrentProfile(), getText(), null, false);
    }
  }

  protected abstract @Nullable AnalysisScope getScope(Project project, PsiFile file);

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
