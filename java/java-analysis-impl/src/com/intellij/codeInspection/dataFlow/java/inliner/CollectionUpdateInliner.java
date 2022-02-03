/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.problems.MutabilityProblem;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public class CollectionUpdateInliner implements CallInliner {
  private static final CallMatcher COLLECTION_REMOVEIF = CallMatcher.instanceCall(
    CommonClassNames.JAVA_UTIL_COLLECTION, "removeIf").parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE);

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (COLLECTION_REMOVEIF.test(call)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return false;
      PsiType elementType = PsiUtil.substituteTypeParameter(qualifier.getType(), CommonClassNames.JAVA_UTIL_COLLECTION, 0, true);
      DfType elementDfType = DfTypes.typedObject(elementType, DfaPsiUtil.getTypeNullability(elementType));
      PsiExpression predicate = call.getArgumentList().getExpressions()[0];
      DfaVariableValue result = builder.createTempVariable(PsiType.BOOLEAN);
      builder
        .assignAndPop(result, DfTypes.FALSE)
        .pushExpression(qualifier) // stack: qualifier
        .ensure(RelationType.IS, Mutability.MUTABLE.asDfType(), new MutabilityProblem(call, true), null)
        .evaluateFunction(predicate)
        .unwrap(SpecialField.COLLECTION_SIZE) // stack: qualifier.size
        .dup() // stack: qualifier.size qualifier.size
        .push(DfTypes.intValue(0)) // stack: qualifier.size qualifier.size 0
        .ifCondition(RelationType.GT)
          .doWhileUnknown()
            .push(elementDfType)
            .invokeFunction(1, predicate)
            .ifConditionIs(true)
              .pushUnknown()
              .assign()
              .assignAndPop(result, DfTypes.TRUE)
            .end()
          .end()
        .end()
        .pop()
        .push(result);
      return true;
    }
    return false;
  }
}
