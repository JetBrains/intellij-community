// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getLOperands;
import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getROperands;

public final class SplitFilterAction extends PsiUpdateModCommandAction<PsiJavaToken> {
  private static final Logger LOG = Logger.getInstance(SplitFilterAction.class.getName());

  public SplitFilterAction() {
    super(PsiJavaToken.class);
  }
  
  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaToken element) {
    final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(element, true, false);
    if (expression == null || expression.getOperands().length < 2) return null;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiLambdaExpression lambda)) return null;
    if (lambda.getParameterList().getParametersCount() != 1) return null;
    parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());

    if (!(parent instanceof PsiExpressionList)) return null;
    final PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression call)) return null;
    if (!MergeFilterChainAction.isFilterCall(call) || hasPatternVariablesUsedAfterSplit(expression, element)) return null;
    
    return Presentation.of(JavaBundle.message("intention.split.filter.text"));
  }

  private static boolean hasPatternVariablesUsedAfterSplit(@NotNull PsiPolyadicExpression expression, @NotNull PsiElement token) {
    List<PsiExpression> afterOperands = new ArrayList<>();
    for (PsiElement after = token; after != null; after = after.getNextSibling()) {
      if (after instanceof PsiExpression) {
        afterOperands.add((PsiExpression)after);
      }
    }
    for (PsiElement child = expression.getFirstChild(); child != token; child = child.getNextSibling()) {
      if (child instanceof PsiExpression) {
        for (PsiPatternVariable variable : JavaPsiPatternUtil.getExposedPatternVariables((PsiExpression)child)) {
          for (PsiExpression operand : afterOperands) {
            if (VariableAccessUtils.variableIsUsed(variable, operand)) return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.split.filter.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaToken token, @NotNull ModPsiUpdater updater) {
    final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(token, true, false);

    final PsiLambdaExpression originalLambdaExpression = PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression.class);
    LOG.assertTrue(originalLambdaExpression != null);
    final String lambdaParameterName = originalLambdaExpression.getParameterList().getParameters()[0].getName();
    final PsiElement originalLambdaExpressionBody = originalLambdaExpression.getBody();
    LOG.assertTrue(originalLambdaExpressionBody != null);

    final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
    LOG.assertTrue(methodCallExpression != null, expression);
    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    LOG.assertTrue(qualifierExpression != null);

    final PsiMethodCallExpression newFilterCall = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(context.project())
        .createExpressionFromText("a.filter(" + lambdaParameterName + " -> x)", methodCallExpression);
    final PsiLambdaExpression newFilterLambda = (PsiLambdaExpression)newFilterCall.getArgumentList().getExpressions()[0];
    final PsiExpression filterCallQualifier = newFilterCall.getMethodExpression().getQualifierExpression();
    LOG.assertTrue(filterCallQualifier != null);
    final PsiElement newFilterLambdaBody = newFilterLambda.getBody();
    LOG.assertTrue(newFilterLambdaBody != null);

    final Collection<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(expression, PsiComment.class);
    final int separatorOffset = token.getTextOffset();
    for (PsiComment comment : comments) {
      if (comment.getTextOffset() < separatorOffset) {
        newFilterLambda.getParent().add(comment);
      }
      else {
        originalLambdaExpression.addBefore(comment, originalLambdaExpressionBody);
      }
    }

    PsiExpression rOperands = getROperands(expression, token);
    PsiExpression lOperands = getLOperands(expression, token);
    originalLambdaExpressionBody.replace(rOperands);
    newFilterLambdaBody.replace(lOperands);
    filterCallQualifier.replace(qualifierExpression);
    qualifierExpression.replace(newFilterCall);
  }

}
