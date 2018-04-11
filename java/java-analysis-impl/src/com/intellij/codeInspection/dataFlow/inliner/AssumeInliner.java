// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

/**
 * JUnit4 Assume.assumeNotNull is a vararg method and each passed null will make the call failing
 */
public class AssumeInliner implements CallInliner {
  private static final CallMatcher ASSUME_NOT_NULL = CallMatcher.staticCall("org.junit.Assume", "assumeNotNull");

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (ASSUME_NOT_NULL.test(call) && MethodCallUtils.isVarArgCall(call)) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      for (PsiExpression arg : args) {
        builder.pushExpression(arg)
               .checkNotNull(arg, NullabilityProblemKind.assumeNotNull)
               .pop();
      }
      builder.pushUnknown();
      return true;
    }
    return false;
  }
}
