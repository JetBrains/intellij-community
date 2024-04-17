// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ReplaceWithTypePatternFix extends PsiUpdateModCommandAction<PsiReferenceExpression> {
  private final @NotNull SmartPsiElementPointer<PsiClass> myResolvedExprClass;
  private final @NlsSafe String myPatternVarName;

  public ReplaceWithTypePatternFix(@NotNull PsiReferenceExpression exprToReplace, @NotNull PsiClass resolvedExprClass,
                                   @NotNull String patternVarName) {
    super(exprToReplace);
    myResolvedExprClass = SmartPointerManager.createPointer(resolvedExprClass);
    myPatternVarName = patternVarName;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression expression) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, expression) || !(expression.getParent() instanceof PsiCaseLabelElementList)) {
      return null;
    }
    PsiClass resolvedExprClass = getResolvedExprClass();
    if (resolvedExprClass == null) return Presentation.of(getFamilyName());
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", resolvedExprClass.getName() + " " + myPatternVarName));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReferenceExpression exprToReplace, @NotNull ModPsiUpdater updater) {
    PsiClass resolvedExprClass = getResolvedExprClass();
    if (resolvedExprClass == null) return;
    PsiExpression newExpr = JavaPsiFacade.getElementFactory(resolvedExprClass.getProject())
      .createExpressionFromText("x instanceof " + resolvedExprClass.getName() + " " + myPatternVarName, resolvedExprClass);
    if (newExpr instanceof PsiInstanceOfExpression instanceOfExpression) {
      exprToReplace.replace(Objects.requireNonNull(instanceOfExpression.getPattern()));
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.type.pattern.fix");
  }

  private @Nullable PsiClass getResolvedExprClass() {
    return myResolvedExprClass.getElement();
  }
}
