// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class QualifyThisOrSuperArgumentFix extends PsiUpdateModCommandAction<PsiExpression> {
  protected static final Logger LOG = Logger.getInstance(QualifyThisOrSuperArgumentFix.class);
  protected final PsiClass myPsiClass;

  QualifyThisOrSuperArgumentFix(@NotNull PsiExpression expression, @NotNull PsiClass psiClass) {
    super(expression);
    myPsiClass = psiClass;
  }

  protected abstract String getQualifierText();

  protected abstract PsiExpression getQualifier(PsiManager manager);

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    if (!myPsiClass.isValid()) return null;
    return Presentation.of(
      JavaAnalysisBundle.message("intention.name.qualify.expression", getQualifierText(), myPsiClass.getQualifiedName()));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("qualify.0", getQualifierText());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
    expression.replace(getQualifier(PsiManager.getInstance(context.project())));
  }
}
