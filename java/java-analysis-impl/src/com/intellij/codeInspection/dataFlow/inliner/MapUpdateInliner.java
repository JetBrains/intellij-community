/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
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
        .pop();
      String name = Objects.requireNonNull(call.getMethodExpression().getReferenceName());
      switch (name) {
        case "computeIfAbsent":
          inlineComputeIfAbsent(builder, qualifier, key, function, type);
          break;
        case "computeIfPresent":
          inlineComputeIfPresent(builder, qualifier, key, function, type);
          break;
        case "compute":
          inlineCompute(builder, qualifier, key, function, type);
          break;
        default:
          throw new IllegalStateException("Unsupported name: " + name);
      }
      builder.resultOf(call);
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
        .pop()
        .pushExpression(key)
        .pop()
        .pushExpression(value)
        .boxUnbox(value, ExpectedTypeUtils.findExpectedType(value, false))
        .evaluateFunction(function)
        .pushUnknown()
        .ifNotNull()
        .push(builder.getFactory().createTypeValue(type, Nullability.NOT_NULL))
        .swap()
        .invokeFunction(2, function)
        .end()
        .chain(b -> flushSize(qualifier, b))
        .resultOf(call);
      return true;
    }
    return false;
  }

  private static void flushSize(PsiExpression qualifier, CFGBuilder builder) {
    DfaValueFactory factory = builder.getFactory();
    DfaValue value = factory.createValue(qualifier);
    DfaValue size = SpecialField.COLLECTION_SIZE.createValue(factory, value);
    builder.assignAndPop(size, DfaUnknownValue.getInstance());
  }

  private static void inlineComputeIfAbsent(@NotNull CFGBuilder builder,
                                            PsiExpression qualifier,
                                            PsiExpression key,
                                            PsiExpression function,
                                            PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .pushUnknown() // stack: .. key; get() result
      .ifNull() // stack: .. key
      .invokeFunction(1, function) // stack: .. mapping result
      .chain(b -> flushSize(qualifier, b))
      .elseBranch()
      .pop()
      .push(builder.getFactory().createTypeValue(type, Nullability.NOT_NULL))
      .end();
  }

  private static void inlineComputeIfPresent(@NotNull CFGBuilder builder,
                                             PsiExpression qualifier, PsiExpression key,
                                             PsiExpression function,
                                             PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .pushUnknown() // stack: .. key; get() result
      .ifNotNull() // stack: .. key
      .push(builder.getFactory().createTypeValue(type, Nullability.NOT_NULL))
      .invokeFunction(2, function) // stack: .. mapping result
      .chain(b -> flushSize(qualifier, b))
      .elseBranch()
      .pop()
      .pushNull()
      .end();
  }

  private static void inlineCompute(@NotNull CFGBuilder builder,
                                    PsiExpression qualifier, PsiExpression key,
                                    PsiExpression function,
                                    PsiType type) {
    builder
      .pushExpression(key) // stack: .. key
      .evaluateFunction(function)
      .push(builder.getFactory().createTypeValue(type, Nullability.NULLABLE))
      .invokeFunction(2, function) // stack: .. mapping result
      .chain(b -> flushSize(qualifier, b));
  }
}
