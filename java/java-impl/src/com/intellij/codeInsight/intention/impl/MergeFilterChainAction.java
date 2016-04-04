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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

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
    if (!"filter".equals(methodCallExpression.getMethodExpression().getReferenceName())) return false;

    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length != 1) return false;
    if (!(expressions[0] instanceof PsiLambdaExpression)) return false;
    final PsiElement lambdaBody = ((PsiLambdaExpression)expressions[0]).getBody();
    if (!(lambdaBody instanceof PsiExpression)) return false;

    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 1 &&
        InheritanceUtil.isInheritor(containingClass, false, CommonClassNames.JAVA_UTIL_STREAM_STREAM) &&
        InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE)) {
      return true;
    }

    return false;
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

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    try {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      final PsiMethodCallExpression filterCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      LOG.assertTrue(filterCall != null);

      final PsiMethodCallExpression filterToMerge = getFilterToMerge(filterCall);
      LOG.assertTrue(filterToMerge != null);

      final PsiMethodCallExpression callToStay = filterCall.getTextLength() < filterToMerge.getTextLength() ? filterCall : filterToMerge;
      final PsiMethodCallExpression callToEliminate = callToStay == filterCall ? filterToMerge : filterCall;

      final PsiLambdaExpression targetLambda = (PsiLambdaExpression)callToStay.getArgumentList().getExpressions()[0];
      final PsiParameter[] parameters = targetLambda.getParameterList().getParameters();
      final String name = parameters.length > 0 ? parameters[0].getName() : null;

      final PsiLambdaExpression sourceLambda = (PsiLambdaExpression)callToEliminate.getArgumentList().getExpressions()[0];
      if (name != null) {
        final PsiParameter[] sourceLambdaParams = sourceLambda.getParameterList().getParameters();
        if (sourceLambdaParams.length > 0 && !name.equals(sourceLambdaParams[0].getName())) {
          for (PsiReference reference : ReferencesSearch.search(sourceLambdaParams[0]).findAll()) {
            final PsiElement referenceElement = reference.getElement();
            if (referenceElement instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)referenceElement).handleElementRename(name);
            }
          }
        }
      }

      PsiElement targetBody = targetLambda.getBody();
      LOG.assertTrue(targetBody instanceof PsiExpression);
      final PsiElement sourceLambdaBody = sourceLambda.getBody();

      LOG.assertTrue(sourceLambdaBody instanceof PsiExpression);


      final PsiExpression compoundExpression = JavaPsiFacade.getElementFactory(project)
        .createExpressionFromText(targetBody.getText() + " && " + sourceLambdaBody.getText(), sourceLambda);
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
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

}
