// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class CollectionMethodInliner implements CallInliner {
  private static final CallMatcher CLEAR = anyOf(instanceCall(JAVA_UTIL_COLLECTION, "clear").parameterCount(0),
                                                 instanceCall(JAVA_UTIL_MAP, "clear").parameterCount(0));

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    if (CLEAR.matches(call)) {
      return inlineClear(builder, qualifier, call);
    }
    return false;
  }

  private static boolean inlineClear(@NotNull CFGBuilder builder,
                                     @NotNull PsiExpression qualifier,
                                     @NotNull PsiMethodCallExpression call) {
    DfaValueFactory factory = builder.getFactory();
    DfaValue qualifierVar = factory.createValue(qualifier);
    if (qualifierVar instanceof DfaVariableValue) {
      builder.pushExpression(qualifier)
        .call(call)
        .push(SpecialField.COLLECTION_SIZE.createValue(factory, qualifierVar))
        .push(DfTypes.intValue(0))
        .assign()
        .pop();
      return true;
    }
    return false;
  }
}
