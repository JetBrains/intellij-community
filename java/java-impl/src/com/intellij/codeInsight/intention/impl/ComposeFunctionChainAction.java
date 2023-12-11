// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ComposeFunctionChainAction extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  public ComposeFunctionChainAction() {
    super(PsiMethodCallExpression.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    PsiMethodCallExpression nearCall =
      PsiTreeUtil.getParentOfType(context.findLeaf(), PsiMethodCallExpression.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if (nearCall != call) return null;
    if (!"apply".equals(call.getMethodExpression().getReferenceName())) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    if(!CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(aClass.getQualifiedName()) &&
       !CommonClassNames.JAVA_UTIL_FUNCTION_BI_FUNCTION.equals(aClass.getQualifiedName())) {
      return null;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
    if (!(parent instanceof PsiExpressionList) || ((PsiExpressionList)parent).getExpressionCount() != 1) return null;

    PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression)) return null;

    PsiMethod outerMethod = ((PsiMethodCallExpression)gParent).resolveMethod();
    if (outerMethod == null ||
        !ContainerUtil.and(outerMethod.getThrowsList().getReferencedTypes(), ExceptionUtil::isUncheckedException)) {
      return null;
    }
    return Presentation.of(JavaBundle.message("intention.compose.function.text"));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.compose.function.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiElement outer = call.getParent().getParent();
    if(!(outer instanceof PsiMethodCallExpression outerCall)) return;
    PsiMethod outerMethod = outerCall.resolveMethod();
    if(outerMethod == null) return;
    PsiClass outerClass = outerMethod.getContainingClass();
    if(outerClass == null) return;
    String outerClassName = outerClass.getQualifiedName();

    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    PsiExpression outerQualifier = outerCall.getMethodExpression().getQualifierExpression();
    CommentTracker ct = new CommentTracker();

    String reference;
    if(outerMethod.getName().equals("apply") && CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(outerClassName)) {
      reference = outerQualifier == null ? "this" : ct.text(outerQualifier);
    } else if(outerMethod.hasModifierProperty(PsiModifier.STATIC)) {
      reference = outerClassName + "::" + outerMethod.getName();
    } else {
      reference = outerQualifier == null ? "this" : ct.text(outerQualifier)+"::"+outerMethod.getName();
    }
    String resultQualifier = qualifier != null ? ct.text(qualifier) + "." : "";

    String replacement = resultQualifier + "andThen(" + reference + ").apply" + ct.text(call.getArgumentList());

    PsiElement result = ct.replaceAndRestoreComments(outer, replacement);
    result = CodeStyleManager.getInstance(context.project()).reformat(result);
    PsiElement applyElement = ((PsiMethodCallExpression)result).getMethodExpression().getReferenceNameElement();
    if(applyElement != null) {
      updater.moveTo(applyElement.getTextOffset() + applyElement.getTextLength());
    }
  }

}
