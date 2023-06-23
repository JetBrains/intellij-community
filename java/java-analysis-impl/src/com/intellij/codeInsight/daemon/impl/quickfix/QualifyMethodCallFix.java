// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QualifyMethodCallFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private final String myQualifierText;

  public QualifyMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull String qualifierText) {
    super(call);
    myQualifierText = qualifierText;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    return Presentation.of(QuickFixBundle.message("qualify.method.call.fix", myQualifierText)).withFixAllOption(this);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("qualify.method.call.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiReferenceExpression expression = call.getMethodExpression();
    expression.setQualifierExpression(JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText(myQualifierText, null));
  }
}