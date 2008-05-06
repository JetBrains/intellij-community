/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RecursiveCallParameterWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> element, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.BASIC && location.getCompletionType() != CompletionType.SMART) return 0;

    final PsiMethod positionMethod = JavaCompletionUtil.POSITION_METHOD.getValue(location);
    if (positionMethod == null) return 0;

    final PsiMethodCallExpression expression =
        PsiTreeUtil.getParentOfType(location.getCompletionParameters().getPosition(), PsiMethodCallExpression.class);
    if (expression == null) return 0;

    final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
    boolean isDelegate = qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression);

    final Object object = element.getObject();
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos != null) {
      final PsiType itemType = JavaCompletionUtil.getPsiType(object);
      if (itemType != null) {
        for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
          if (positionMethod.equals(expectedInfo.getCalledMethod()) && expectedInfo.getType().isAssignableFrom(itemType)) {
            return isDelegate ? 2 : 0;
          }
        }
      }
    }

    final PsiElement position = location.getCompletionParameters().getPosition();
    if (PsiTreeUtil.isAncestor(positionMethod, position, false) && PsiTreeUtil.isAncestor(expression.getMethodExpression(), position, false)) {
      return isDelegate ? 2 : 0;
    }

    return 1;
  }
}
