// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ReplaceWithTypePatternFix extends BaseIntentionAction {
  @NotNull private final SmartPsiElementPointer<PsiReferenceExpression> myExprToReplace;
  @NotNull private final SmartPsiElementPointer<PsiClass> myResolvedExprClass;
  @NlsSafe private final String myPatternVarName;

  public ReplaceWithTypePatternFix(@NotNull PsiReferenceExpression exprToReplace, @NotNull PsiClass resolvedExprClass,
                                   @NotNull String patternVarName) {
    myExprToReplace = SmartPointerManager.createPointer(exprToReplace);
    myResolvedExprClass = SmartPointerManager.createPointer(resolvedExprClass);
    myPatternVarName = patternVarName;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiReferenceExpression exprToReplace = myExprToReplace.getElement();
    PsiClass resolvedExprClass = myResolvedExprClass.getElement();
    if (exprToReplace == null || resolvedExprClass == null) return null;
    return new ReplaceWithTypePatternFix(PsiTreeUtil.findSameElementInCopy(exprToReplace, target),
                                         resolvedExprClass, myPatternVarName);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiReferenceExpression expression = getExprToReplace();
    if (expression == null) return false;
    return HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(expression) && expression.getParent() instanceof PsiCaseLabelElementList;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiReferenceExpression exprToReplace = getExprToReplace();
    if (exprToReplace == null) return;
    PsiClass resolvedExprClass = getResolvedExprClass();
    if (resolvedExprClass == null) return;
    PsiExpression newExpr = JavaPsiFacade.getElementFactory(resolvedExprClass.getProject())
      .createExpressionFromText("x instanceof " + resolvedExprClass.getName() + " " + myPatternVarName, resolvedExprClass);
    if (newExpr instanceof PsiInstanceOfExpression) {
      exprToReplace.replace(Objects.requireNonNull(((PsiInstanceOfExpression)newExpr).getPattern()));
    }
  }

  @Override
  public @NotNull String getText() {
    PsiClass resolvedExprClass = getResolvedExprClass();
    if (resolvedExprClass == null) return getFamilyName();
    return CommonQuickFixBundle.message("fix.replace.with.x", resolvedExprClass.getName() + " " + myPatternVarName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.type.pattern.fix");
  }

  @Nullable
  private PsiReferenceExpression getExprToReplace() {
    return myExprToReplace.getElement();
  }

  @Nullable
  private PsiClass getResolvedExprClass() {
    return myResolvedExprClass.getElement();
  }
}
