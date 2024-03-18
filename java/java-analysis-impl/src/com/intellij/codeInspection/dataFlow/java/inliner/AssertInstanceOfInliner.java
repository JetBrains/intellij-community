// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public final class AssertInstanceOfInliner implements CallInliner {
  private static final CallMatcher ASSERT_INSTANCE_OF = CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertInstanceOf");

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!ASSERT_INSTANCE_OF.matches(call)) return false;
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if (expressions.length < 2 || expressions.length > 3) return false;
    PsiExpression wantedClass = expressions[0];
    PsiExpression objectToTest = expressions[1];
    builder.pushExpression(wantedClass)
      .pushExpression(objectToTest)
      .boxUnbox(objectToTest, PsiType.getJavaLangObject(call.getManager(), call.getResolveScope()))
      .splice(2, 0, 0, 1);
    if (expressions.length == 3) {
      builder.pushExpression(expressions[2]).pop();
    }
    builder.isInstance(null)
      .ensure(RelationType.EQ, DfTypes.TRUE, new ContractFailureProblem(call), CommonClassNames.JAVA_LANG_ASSERTION_ERROR)
      .pop();
    return true;
  }

  @Override
  public boolean mayInferPreciseType(@NotNull PsiExpression expression) {
    PsiParameter argument = MethodCallUtils.getParameterForArgument(expression);
    return argument != null && ASSERT_INSTANCE_OF.methodMatches((PsiMethod)argument.getDeclarationScope());
  }
}
