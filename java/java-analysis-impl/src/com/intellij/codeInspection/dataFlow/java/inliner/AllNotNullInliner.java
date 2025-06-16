// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

/**
 * Apache Commons ObjectUtils null-checks
 */
public class AllNotNullInliner implements CallInliner {
  private static final CallMatcher NULL_TESTS =
    anyOf(
      staticCall("org.apache.commons.lang3.ObjectUtils", "allNull").parameterTypes("java.lang.Object..."),
      staticCall("org.apache.commons.lang3.ObjectUtils", "allNotNull").parameterTypes("java.lang.Object..."),
      staticCall("org.apache.commons.lang3.ObjectUtils", "anyNull").parameterTypes("java.lang.Object..."),
      staticCall("org.apache.commons.lang3.ObjectUtils", "anyNotNull").parameterTypes("java.lang.Object...")
    );

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!NULL_TESTS.matches(call) || !MethodCallUtils.isVarArgCall(call)) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    String methodName = call.getMethodExpression().getReferenceName();
    PsiClassType objectType = PsiType.getJavaLangObject(call.getManager(), call.getResolveScope());
    boolean allMatchResult = "allNotNull".equals(methodName) || "allNull".equals(methodName);
    builder.push(DfTypes.booleanValue(!allMatchResult));
    for (PsiExpression arg : args) {
      builder.pushExpression(arg)
        .boxUnbox(arg, objectType);
      if ("allNotNull".equals(methodName) || "anyNull".equals(methodName)) {
        builder.ifNotNull();
      } else {
        builder.ifNull();
      }
    }
    builder.not();
    for (int i = 0; i < args.length; i++) {
      builder.end();
    }
    builder.resultOf(call);
    return true;
  }
}
