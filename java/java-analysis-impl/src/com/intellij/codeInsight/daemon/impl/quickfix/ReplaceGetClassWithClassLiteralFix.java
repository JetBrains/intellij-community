// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceGetClassWithClassLiteralFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  public ReplaceGetClassWithClassLiteralFix(PsiMethodCallExpression expression) {
    super(expression);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
    assert aClass != null;
    PsiExpression classLiteral = JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText(aClass.getName() + ".class", call);
    new CommentTracker().replaceAndRestoreComments(call, classLiteral);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
    if (aClass == null) return null;
    String className = aClass.getName();
    if (className == null) return null;
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", className + "." + PsiKeyword.CLASS)).withPriority(
      PriorityAction.Priority.HIGH);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("replace.get.class.with.class.literal");
  }

  public static void registerFix(PsiMethodCallExpression callExpression, HighlightInfo.Builder errorResult) {
    if (callExpression.getMethodExpression().getQualifierExpression() == null) {
      PsiMethod method = callExpression.resolveMethod();
      if (method != null && PsiTypesUtil.isGetClass(method)) {
        var action = new ReplaceGetClassWithClassLiteralFix(callExpression);
        if (errorResult != null) {
          errorResult.registerFix(action, null, null, null, null);
        }
      }
    }
  }
}
