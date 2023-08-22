// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class RemoveQualifierFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  private final PsiClass myResolved;

  public RemoveQualifierFix(@NotNull PsiReferenceExpression expression, @NotNull PsiClass resolved) {
    super(expression);
    myResolved = resolved;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.qualifier.action.text");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression expression) {
    return myResolved.isValid() && expression.getQualifierExpression() != null
           ? Presentation.of(getFamilyName()).withFixAllOption(this)
           : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression expression, @NotNull ModPsiUpdater updater) {
    Objects.requireNonNull(expression.getQualifierExpression()).delete();
    expression.bindToElement(myResolved);
  }
}
