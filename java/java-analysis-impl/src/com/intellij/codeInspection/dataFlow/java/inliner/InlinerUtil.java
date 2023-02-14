// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;

import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

final class InlinerUtil {
  static boolean isLambdaChainParameterReference(PsiExpression expression, Predicate<? super PsiType> chainTypePredicate) {
    if(!(expression instanceof PsiReferenceExpression)) return false;
    PsiParameter target = tryCast(((PsiReferenceExpression)expression).resolve(), PsiParameter.class);
    if (target == null) return false;
    if (!(target.getParent() instanceof PsiParameterList)) return false;
    PsiLambdaExpression lambda = tryCast(target.getParent().getParent(), PsiLambdaExpression.class);
    if (lambda == null) return false;
    PsiExpressionList list = tryCast(PsiUtil.skipParenthesizedExprUp(lambda.getParent()), PsiExpressionList.class);
    if (list == null) return false;
    PsiMethodCallExpression call = tryCast(list.getParent(), PsiMethodCallExpression.class);
    if (call == null) return false;
    PsiMethodCallExpression qualifierCall = call;
    do {
      qualifierCall = MethodCallUtils.getQualifierMethodCall(qualifierCall);
      if (qualifierCall == null) return false;
    }
    while (qualifierCall.getArgumentList().isEmpty());
    PsiType type = qualifierCall.getType();
    return type != null && chainTypePredicate.test(type);
  }
}
