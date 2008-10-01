/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RecursiveCallParameterWeigher extends CompletionWeigher {

  public Integer weigh(@NotNull final LookupElement element, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.BASIC && location.getCompletionType() != CompletionType.SMART) return 0;
    if (!(element instanceof MutableLookupElement)) return 0;

    final Object object = ((MutableLookupElement)element).getObject();
    if (!(object instanceof PsiModifierListOwner) && !(object instanceof PsiExpression)) return 0;

    final PsiMethod positionMethod = JavaCompletionUtil.POSITION_METHOD.getValue(location);
    if (positionMethod == null) return 0;

    final PsiElement position = location.getCompletionParameters().getPosition();
    final PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class, true, PsiClass.class);
    final PsiReferenceExpression reference = expression != null ? expression.getMethodExpression() : PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class);
    if (reference == null) return 0;

    final PsiExpression qualifier = reference.getQualifierExpression();
    boolean isDelegate = qualifier != null && !(qualifier instanceof PsiThisExpression);
    if (expression != null) {
      final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
      if (expectedInfos != null) {
        final PsiType itemType = JavaCompletionUtil.getPsiType(object);
        if (itemType != null) {
          for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
            if (positionMethod.equals(expectedInfo.getCalledMethod()) && expectedInfo.getType().isAssignableFrom(itemType)) {
              return isDelegate ? 2 : -1;
            }
          }
        }
      }
      return 0;
    }

    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      if (PsiTreeUtil.isAncestor(reference, position, false) &&
          Comparing.equal(method.getName(), positionMethod.getName()) &&
          method.getParameterList().getParametersCount() == positionMethod.getParameterList().getParametersCount()) {
        if (findDeepestSuper(method).equals(findDeepestSuper(positionMethod))) {
          return isDelegate ? 2 : -1;
        }
      }
    }

    return 0;
  }

  @NotNull
  private static PsiMethod findDeepestSuper(@NotNull final PsiMethod method) {
    final PsiMethod first = DeepestSuperMethodsSearch.search(method).findFirst();
    return first == null ? method : first;
  }
}
