// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.*;

public class ForEachCollectionTraversal extends IterableTraversal {
  private static final CallMatcher COLLECTION_TO_ARRAY = CallMatcher.anyOf(
    CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "toArray").parameterCount(0),
    CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "toArray").parameterTypes("T[]"));

  private static final CallMatcher COLLECTION_REMOVE =
    CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "remove").parameterTypes(JAVA_LANG_OBJECT);

  private final PsiParameter myParameter;

  ForEachCollectionTraversal(@Nullable PsiExpression iterable, PsiParameter parameter) {
    super(iterable, true);
    myParameter = parameter;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  @Override
  public boolean isRemoveCall(PsiExpression candidate) {
    candidate = PsiUtil.skipParenthesizedExprDown(candidate);
    if (!(candidate instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)candidate;
    if (!COLLECTION_REMOVE.test(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, myIterable)) return false;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    return ExpressionUtils.isReferenceTo(arg, myParameter);
  }

  @Nullable
  public static ForEachCollectionTraversal fromLoop(@NotNull PsiForeachStatement loop) {
    PsiExpression collection = extractCollectionExpression(loop.getIteratedValue());
    if (collection == null) return null;
    PsiType collectionElement = PsiUtil.substituteTypeParameter(collection.getType(), JAVA_UTIL_COLLECTION, 0, false);
    if (collectionElement == null) return null;
    PsiParameter parameter = loop.getIterationParameter();
    if (!parameter.getType().equals(collectionElement)) return null;
    return new ForEachCollectionTraversal(collection, parameter);
  }

  private static PsiExpression extractCollectionExpression(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression && COLLECTION_TO_ARRAY.test((PsiMethodCallExpression)expression)) {
      return PsiUtil.skipParenthesizedExprDown(((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression());
    }
    if (expression instanceof PsiNewExpression &&
        ConstructionUtils.isReferenceTo(((PsiNewExpression)expression).getClassReference(), JAVA_UTIL_ARRAY_LIST)) {
      PsiExpressionList argumentList = ((PsiNewExpression)expression).getArgumentList();
      if (argumentList != null) {
        PsiExpression[] args = argumentList.getExpressions();
        if (args.length == 1 && InheritanceUtil.isInheritor(args[0].getType(), JAVA_UTIL_COLLECTION)) {
          return args[0];
        }
      }
    }
    if (expression != null && InheritanceUtil.isInheritor(expression.getType(), JAVA_UTIL_COLLECTION)) {
      return expression;
    }
    return null;
  }
}
