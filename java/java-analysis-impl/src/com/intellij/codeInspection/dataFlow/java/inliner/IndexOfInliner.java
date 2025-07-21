// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class IndexOfInliner implements CallInliner {
  private static final CallMatcher INDEX_OF = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "indexOf", "lastIndexOf")
    .parameterCount(1);

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!INDEX_OF.test(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiExpression arg = call.getArgumentList().getExpressions()[0];
    DfaVariableValue res = builder.createTempVariable(PsiTypes.intType());
    RelationType relationType = RelationType.LT;
    if (TypeUtils.isJavaLangString(arg.getType())) {
       String strArg = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(arg), String.class);
      if (strArg == null || strArg.isEmpty()) {
        // a.lastIndexOf(b) could equal to a.length() if b is an empty string
        // also, a.indexOf(b) could be equal to a.length() if both a and b are empty
        relationType = RelationType.LE;
      }
    }
    builder.pushExpression(qualifier)
      .dup()
      .pushExpression(arg)
      .call(call)
      .assignTo(res)
      .splice(2, 0, 0, 1)
      .unwrap(SpecialField.STRING_LENGTH)
      .compare(relationType)
      .ensure(RelationType.EQ, DfTypes.TRUE, new UnsatisfiedConditionProblem() {
      }, null)
      .pop();
    return true;
  }
}
