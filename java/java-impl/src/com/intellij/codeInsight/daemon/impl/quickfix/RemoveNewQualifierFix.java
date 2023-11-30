// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveNewQualifierFix extends PsiUpdateModCommandAction<PsiNewExpression> {
  private final @Nullable PsiClass aClass;

  public RemoveNewQualifierFix(@NotNull PsiNewExpression expression, @Nullable PsiClass aClass) {
    super(expression);
    this.aClass = aClass;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression element) {
    return aClass == null || aClass.isValid() ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression expression, @NotNull ModPsiUpdater updater) {
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.delete();
    }
    if (aClass != null && classReference != null) {
      classReference.bindToElement(aClass);
    }
  }
}
