/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MergeFilterChainAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(MergeFilterChainAction.class.getName());

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull final PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return false;
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return false;
    final PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression)) return false;

    if (!isFilterCall((PsiMethodCallExpression)gParent)) return false;

    return getFilterToMerge((PsiMethodCallExpression)gParent) != null;
  }

  @Nullable
  private static PsiMethodCallExpression getFilterToMerge(PsiMethodCallExpression methodCallExpression) {
    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiMethodCallExpression && isFilterCall((PsiMethodCallExpression)qualifierExpression)) {
      return (PsiMethodCallExpression)qualifierExpression;
    }

    final PsiElement parent = methodCallExpression.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression && isFilterCall((PsiMethodCallExpression)gParent)) {
        return (PsiMethodCallExpression)gParent;
      }
    }

    return null;
  }

  public static boolean isFilterCall(PsiMethodCallExpression methodCallExpression) {
    String name = methodCallExpression.getMethodExpression().getReferenceName();
    if (!"filter".equals(name) && !"anyMatch".equals(name)) return false;

    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length != 1) return false;
    if (!StreamRefactoringUtil.isRefactoringCandidate(expressions[0], true)) return false;

    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    return parameters.length == 1 &&
           InheritanceUtil.isInheritor(containingClass, false, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.merge.filter.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.merge.filter.family");
  }

  @Nullable
  private static PsiLambdaExpression getLambda(PsiMethodCallExpression call) {
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if(expressions.length != 1) return null;
    PsiExpression expression = expressions[0];
    if(expression instanceof PsiLambdaExpression) return (PsiLambdaExpression)expression;
    if (expression instanceof PsiMethodReferenceExpression) {
      return LambdaRefactoringUtil
        .convertMethodReferenceToLambda((PsiMethodReferenceExpression)expression, false, true);
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiMethodCallExpression filterCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    LOG.assertTrue(filterCall != null);

    final PsiMethodCallExpression filterToMerge = getFilterToMerge(filterCall);
    LOG.assertTrue(filterToMerge != null);

    final PsiMethodCallExpression callToStay = filterCall.getTextLength() < filterToMerge.getTextLength() ? filterCall : filterToMerge;
    final PsiMethodCallExpression callToEliminate = callToStay == filterCall ? filterToMerge : filterCall;

    String resultingOperation = callToEliminate.getMethodExpression().getReferenceName();
    LOG.assertTrue(resultingOperation != null);

    final PsiLambdaExpression targetLambda = getLambda(callToStay);
    LOG.assertTrue(targetLambda != null, callToStay);
    final PsiParameter[] parameters = targetLambda.getParameterList().getParameters();
    final String name = parameters.length > 0 ? parameters[0].getName() : null;

    final PsiLambdaExpression sourceLambda = getLambda(callToEliminate);
    LOG.assertTrue(sourceLambda != null, callToEliminate);
    if (name != null) {
      final PsiParameter[] sourceLambdaParams = sourceLambda.getParameterList().getParameters();
      if (sourceLambdaParams.length > 0 && !name.equals(sourceLambdaParams[0].getName())) {
        for (PsiReference reference : ReferencesSearch.search(sourceLambdaParams[0]).findAll()) {
          final PsiElement referenceElement = reference.getElement();
          if (referenceElement instanceof PsiReferenceExpression) {
            ExpressionUtils.bindReferenceTo((PsiReferenceExpression)referenceElement, name);
          }
        }
      }
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiElement nameElement = callToStay.getMethodExpression().getReferenceNameElement();
    LOG.assertTrue(nameElement != null);
    if(!resultingOperation.equals(nameElement.getText())) {
      nameElement.replace(factory.createIdentifier(resultingOperation));
    }

    PsiElement targetBody = targetLambda.getBody();
    LOG.assertTrue(targetBody instanceof PsiExpression);
    final PsiElement sourceLambdaBody = sourceLambda.getBody();

    LOG.assertTrue(sourceLambdaBody instanceof PsiExpression);

    final PsiExpression compoundExpression = factory
      .createExpressionFromText(
        ParenthesesUtils.getText((PsiExpression)targetBody, ParenthesesUtils.OR_PRECEDENCE) + " && " +
        ParenthesesUtils.getText((PsiExpression)sourceLambdaBody, ParenthesesUtils.OR_PRECEDENCE), sourceLambda);
    targetBody = targetBody.replace(compoundExpression);
    CodeStyleManager.getInstance(project).reformat(targetBody);

    final PsiExpression qualifierExpression = callToEliminate.getMethodExpression().getQualifierExpression();
    LOG.assertTrue(qualifierExpression != null, callToEliminate);
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(callToEliminate, PsiComment.class);
    for (PsiComment comment : comments) {
      final TextRange commentRange = comment.getTextRange();
      if (!sourceLambdaBody.getTextRange().contains(commentRange) &&
          !qualifierExpression.getTextRange().contains(commentRange)) {
        targetBody.add(comment);
      }
    }
    callToEliminate.replace(qualifierExpression);
  }

}
