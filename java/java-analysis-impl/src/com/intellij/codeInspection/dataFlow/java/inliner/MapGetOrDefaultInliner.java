// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public class MapGetOrDefaultInliner implements CallInliner {
  private static final CallMatcher MAP_GET_OR_DEFAULT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "getOrDefault").parameterCount(2);

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!MAP_GET_OR_DEFAULT.test(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (arguments.length != 2) return false;
    PsiExpression key = arguments[0];
    PsiExpression defaultValue = arguments[1];
    PsiType type = call.getType();
    if (type == null) return false;
    Nullability nullability = type.getNullability().nullability();
    PsiType defaultValueType = defaultValue.getType();
    // if `V` is notnull in `Map<K, V>`, then we take nullability from `defaultValue`
    if (nullability == Nullability.NOT_NULL && defaultValueType != null) {
      nullability = defaultValueType.getNullability().nullability();
    }
    builder
      .pushExpression(qualifier) // stack: .. qualifier
      .pushExpression(key) // stack: .. qualifier key
      .pop() // stack: .. qualifier
      .pushExpression(defaultValue) // stack: .. qualifier `default value`
      .boxUnbox(defaultValue, type)
      .swap() // stack: .. `default value` qualifier
      .unwrap(SpecialField.COLLECTION_SIZE) // stack: .. `default value` qualifier.size
      .push(DfTypes.intValue(0))
      .ifCondition(RelationType.EQ) // stack: .. `default value`
      .elseBranch()
      .pop() // stack: ..
      .push(DfTypes.typedObject(type, nullability)) // stack: `unknown with some nullability`
      .end()
      .resultOf(call);
    return true;
  }
}
