// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Inline class.isInstance(obj) and class.isAssignableFrom(obj.getClass())
 */
public class ClassMethodsInliner implements CallInliner {
  private static final CallMatcher IS_ASSIGNABLE_FROM = CallMatcher.instanceCall(JAVA_LANG_CLASS, "isAssignableFrom").parameterTypes(
    JAVA_LANG_CLASS);
  private static final CallMatcher IS_INSTANCE = CallMatcher.instanceCall(JAVA_LANG_CLASS, "isInstance").parameterTypes(
    JAVA_LANG_OBJECT);
  private static final CallMatcher OBJECT_GET_CLASS = CallMatcher.instanceCall(JAVA_LANG_OBJECT, "getClass").parameterCount(0);


  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    if (IS_ASSIGNABLE_FROM.matches(call)) {
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      PsiMethodCallExpression nestedCall = tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiMethodCallExpression.class);
      PsiExpression getClassQualifier =
        OBJECT_GET_CLASS.matches(nestedCall) ? nestedCall.getMethodExpression().getQualifierExpression() : null;
      if (getClassQualifier != null) {
        builder.pushExpression(qualifier)
               .pushExpression(getClassQualifier)
               .checkNotNull(nestedCall, NullabilityProblemKind.callNPE);
      }
      else {
        builder.pushExpression(qualifier)
               .pushExpression(arg)
               .checkNotNull(arg, NullabilityProblemKind.passingNullableToNotNullParameter)
               .objectOf();
      }
      builder.swap()
             .checkNotNull(call, NullabilityProblemKind.callNPE)
             .isInstance(call);
      return true;
    }
    else if (IS_INSTANCE.matches(call)) {
      builder.pushExpression(qualifier)
             .pushExpression(call.getArgumentList().getExpressions()[0])
             .swap()
             .checkNotNull(call, NullabilityProblemKind.callNPE)
             .isInstance(call);
      return true;
    }
    return false;
  }
}
