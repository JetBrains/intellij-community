/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
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

    if (MergeFilterChainAction.isFilterCall((PsiMethodCallExpression)gParent)) {
      return true;
    }

    return false;
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
    try {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      final PsiJavaToken token = (PsiJavaToken)element;
      final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(element, true, false);

      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression.class);
      LOG.assertTrue(lambdaExpression != null);
      final String lambdaParameterName = lambdaExpression.getParameterList().getParameters()[0].getName();

      final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
      LOG.assertTrue(methodCallExpression != null, expression);
      
      PsiExpression lOperand = getLOperands(expression, token);
      PsiExpression rOperand = getROperands(expression, token);

      final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(expression, PsiComment.class);

      final PsiMethodCallExpression chainedCall =
        (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a.filter(" + lambdaParameterName + " -> x)", expression);
      final PsiExpression argExpression = chainedCall.getArgumentList().getExpressions()[0];
      final PsiElement rReplaced = ((PsiLambdaExpression)argExpression).getBody().replace(rOperand);

      final PsiExpression compoundArg = methodCallExpression.getArgumentList().getExpressions()[0];

      final int separatorOffset = token.getTextOffset();
      for (PsiComment comment : comments) {
        if (comment.getTextOffset() < separatorOffset) {
          compoundArg.getParent().add(comment);
        }
        else {
          rReplaced.getParent().add(comment);
        }
      }

      ((PsiLambdaExpression)compoundArg).getBody().replace(lOperand);

      chainedCall.getMethodExpression().getQualifierExpression().replace(methodCallExpression);
      methodCallExpression.replace(chainedCall);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

}
