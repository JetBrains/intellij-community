/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MapUpdateInliner implements CallInliner {
  private static final CallMatcher MAP_COMPUTE = CallMatcher.instanceCall(
    CommonClassNames.JAVA_UTIL_MAP, "computeIfAbsent", "computeIfPresent", "compute").parameterCount(2);
  private static final CallMatcher MAP_MERGE = CallMatcher.instanceCall(
    CommonClassNames.JAVA_UTIL_MAP, "merge").parameterCount(3);

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (MAP_COMPUTE.test(call)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return false;
      PsiType type = call.getType();
      if (type == null) return false;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression key = args[0];
      PsiExpression function = args[1];
      builder
        .pushExpression(qualifier)
        .checkNotNull(call, NullabilityProblemKind.callNPE)
        .pop();
      String name = Objects.requireNonNull(call.getMethodExpression().getReferenceName());
      switch (name) {
        case "computeIfAbsent":
          inlineComputeIfAbsent(builder, key, function, type);
          break;
        case "computeIfPresent":
          inlineComputeIfPresent(builder, key, function, type);
          break;
        case "compute":
          inlineCompute(builder, key, function, type);
          break;
        default:
          throw new IllegalStateException("Unsupported name: " + name);
      }
      return true;
    }
    if (MAP_MERGE.test(call)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return false;
      PsiType type = call.getType();
      if (type == null) return false;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression key = args[0];
      PsiExpression value = args[1];
      PsiExpression function = args[2];
      builder
        .pushExpression(qualifier)
        .checkNotNull(call, NullabilityProblemKind.callNPE)
        .pop()
        .pushExpression(key)
        .pop()
        .pushExpression(value)
        .checkNotNull(value, NullabilityProblemKind.passingNullableToNotNullParameter)
        .evaluateFunction(function)
        .pushUnknown()
        .ifNotNull()
        .push(builder.getFactory().createTypeValue(type, Nullness.NOT_NULL))
        .swap()
        .invokeFunction(2, function)
        .endIf()
        .flushFields();
      return true;
    }
    return false;
  }

  private static void inlineComputeIfAbsent(@NotNull CFGBuilder builder, PsiExpression key, PsiExpression function, PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .pushUnknown() // stack: .. key; get() result
      .ifNull() // stack: .. key
      .invokeFunction(1, function) // stack: .. mapping result
      .flushFields()
      .elseBranch()
      .pop()
      .push(builder.getFactory().createTypeValue(type, Nullness.NOT_NULL))
      .endIf();
  }

  private static void inlineComputeIfPresent(@NotNull CFGBuilder builder,
                                             PsiExpression key,
                                             PsiExpression function,
                                             PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .pushUnknown() // stack: .. key; get() result
      .ifNotNull() // stack: .. key
      .push(builder.getFactory().createTypeValue(type, Nullness.NOT_NULL))
      .invokeFunction(2, function) // stack: .. mapping result
      .flushFields()
      .elseBranch()
      .pop()
      .pushNull()
      .endIf();
  }

  private static void inlineCompute(@NotNull CFGBuilder builder,
                                    PsiExpression key,
                                    PsiExpression function,
                                    PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .push(builder.getFactory().createTypeValue(type, Nullness.NULLABLE))
      .invokeFunction(2, function) // stack: .. mapping result
      .flushFields();
  }
}
