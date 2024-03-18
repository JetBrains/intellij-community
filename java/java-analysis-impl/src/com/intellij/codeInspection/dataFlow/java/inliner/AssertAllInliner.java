// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import com.siyeh.ig.junit.JUnitCommonClassNames;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

/**
 * JUnit5 Assertions.assertAll
 */
public class AssertAllInliner implements CallInliner {
  private static final CallMatcher ASSERT_ALL =
    anyOf(
      staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertAll").parameterTypes("org.junit.jupiter.api.function.Executable..."),
      staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertAll")
        .parameterTypes(CommonClassNames.JAVA_LANG_STRING, "org.junit.jupiter.api.function.Executable...")
    );


  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!ASSERT_ALL.matches(call) || !MethodCallUtils.isVarArgCall(call)) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      if (i == 0 && TypeUtils.isJavaLangString(arg.getType())) {
        builder.pushExpression(arg, NullabilityProblemKind.noProblem).pop();
      }
      else {
        builder.evaluateFunction(arg);
      }
    }
    DfaVariableValue result = builder.createTempVariable(PsiTypes.booleanType());
    builder.assignAndPop(result, DfTypes.FALSE);
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      if (i == 0 && TypeUtils.isJavaLangString(arg.getType())) continue;
      builder
        .doTry(call)
        .invokeFunction(0, arg)
        .pop()
        .catchAll()
        .assignAndPop(result, DfTypes.TRUE)
        .end();
    }
    PsiType throwable = JavaPsiFacade.getElementFactory(call.getProject())
                                     .createTypeByFQClassName("org.opentest4j.MultipleFailuresError", call.getResolveScope());
    builder.push(result)
           .ifConditionIs(true)
           .doThrow(throwable)
           .end()
           .pushUnknown(); // void method result
    return true;
  }
}
