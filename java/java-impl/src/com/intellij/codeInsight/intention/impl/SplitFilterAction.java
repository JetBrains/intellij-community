// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getLOperands;
import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getROperands;

public class SplitFilterAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(SplitFilterAction.class.getName());

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(element, true, false);
    if (expression == null || expression.getOperands().length < 2) return false;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiLambdaExpression)) return false;
    if (((PsiLambdaExpression)parent).getParameterList().getParametersCount() != 1) return false;
    parent = parent.getParent();

    if (!(parent instanceof PsiExpressionList)) return false;
    final PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression)) return false;

    return MergeFilterChainAction.isFilterCall((PsiMethodCallExpression)gParent);
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.split.filter.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.split.filter.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
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

    final PsiMethodCallExpression newFilterCall = (PsiMethodCallExpression)
      JavaPsiFacade.getElementFactory(project).createExpressionFromText("a.filter(" + lambdaParameterName + " -> x)", methodCallExpression);
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

    originalLambdaExpressionBody.replace(getROperands(expression, token));
    newFilterLambdaBody.replace(getLOperands(expression, token));
    filterCallQualifier.replace(qualifierExpression);
    qualifierExpression.replace(newFilterCall);
  }

}
