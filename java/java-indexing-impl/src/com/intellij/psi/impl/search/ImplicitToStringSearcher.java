// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.RequestResultProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class ImplicitToStringSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters parameters, @NotNull Processor<PsiReference> consumer) {
    PsiMethod method = parameters.getMethod();
    if (!"toString".equals(method.getName()) || method.getParameters().length != 0) {
      return;
    }
    PsiClass targetClass = ReadAction.compute(() -> method.getContainingClass());
    if (targetClass == null) {
      return;
    }

    parameters.getOptimizer().searchWord("+", parameters.getScopeDeterminedByUser(), UsageSearchContext.IN_CODE, true,
                                         new RequestResultProcessor("java.toString") {
                                           @Override
                                           public boolean processTextOccurrence(@NotNull PsiElement element,
                                                                                int offsetInElement,
                                                                                @NotNull Processor<PsiReference> consumer) {
                                             if (!(element instanceof PsiJavaToken)) return true;
                                             PsiBinaryExpression binaryExpression = ObjectUtils.tryCast(element.getParent(), PsiBinaryExpression.class);
                                             if (binaryExpression == null) return true;
                                             PsiExpression rOperand = binaryExpression.getROperand();
                                             if (rOperand == null) return true;
                                             return processBinaryExpression(binaryExpression.getLOperand(), rOperand, consumer, targetClass);
                                           }
                                         });
  }

  private static boolean processBinaryExpression(@NotNull PsiExpression lhs,
                                                 @NotNull PsiExpression rhs,
                                                 @NotNull Processor<PsiReference> consumer,
                                                 @NotNull PsiClass psiClass) {
    if (!processBinaryExpressionInOneDirection(lhs, rhs, consumer, psiClass)) {
      return false;
    }
    return processBinaryExpressionInOneDirection(rhs, lhs, consumer, psiClass);
  }

  private static boolean processBinaryExpressionInOneDirection(@NotNull PsiExpression stringExpr,
                                                               @NotNull PsiExpression expr,
                                                               @NotNull Processor<PsiReference> consumer,
                                                               @NotNull PsiClass psiClass) {
    PsiType strType = stringExpr.getType();
    if (strType == null || !strType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return true;

    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expr.getType());
    if (aClass != null && PsiUtil.preferCompiledElement(aClass) == psiClass) {
      return consumer.process(expr.getReference());
    }
    return true;
  }
}
