// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QualifyWithThisFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  private final PsiClass myContainingClass;

  public QualifyWithThisFix(@NotNull PsiClass containingClass, @NotNull PsiReferenceExpression expression) {
    super(expression);
    myContainingClass = containingClass;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression element) {
    return myContainingClass.isValid() ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("qualify.with.0.this", myContainingClass.getName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression element, @NotNull ModPsiUpdater updater) {
    final PsiThisExpression thisExpression =
      RefactoringChangeUtil.createThisExpression(PsiManager.getInstance(context.project()), myContainingClass);
    element.setQualifierExpression(thisExpression);
  }
}
