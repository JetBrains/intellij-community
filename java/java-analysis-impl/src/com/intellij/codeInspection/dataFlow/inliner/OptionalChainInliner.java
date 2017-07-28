/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaOptionalValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;

/**
 * An inliner which is capable to inline some Optional chains like
 * {@code Optional.of(xyz).map(lambda).filter(lambda).flatMap(lambda).orElseGet(lambda)}
 * <p>
 * TODO support Guava optional
 * TODO support primitive Optionals
 */
public class OptionalChainInliner implements CallInliner {
  private static final CallMatcher OPTIONAL_OR_ELSE = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "orElse").parameterCount(1);
  private static final CallMatcher OPTIONAL_OR_ELSE_GET = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1);
  private static final CallMatcher OPTIONAL_OR = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "or").parameterCount(1); // Java 9
  private static final CallMatcher OPTIONAL_IF_PRESENT = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "ifPresent").parameterCount(1);
  private static final CallMatcher OPTIONAL_FILTER = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "filter").parameterCount(1);
  private static final CallMatcher OPTIONAL_MAP = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "map").parameterCount(1);
  private static final CallMatcher OPTIONAL_FLAT_MAP = CallMatcher.instanceCall(JAVA_UTIL_OPTIONAL, "flatMap").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF = CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "of", "ofNullable").parameterCount(1);
  private static final CallMatcher OPTIONAL_EMPTY = CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "empty").parameterCount(0);

  private static final CallMapper<BiConsumer<CFGBuilder, PsiMethodCallExpression>> TERMINAL_MAPPER =
    new CallMapper<BiConsumer<CFGBuilder, PsiMethodCallExpression>>()
    .register(OPTIONAL_OR_ELSE, (builder, call) -> {
      PsiExpression argument = call.getArgumentList().getExpressions()[0];
      builder.pushExpression(argument) // stack: .. optValue, elseValue
        .boxUnbox(argument, call.getType())
        .splice(2, 0, 1, 1) // stack: .. elseValue, optValue, optValue
        .ifNotNull()
        .swap() // stack: .. optValue, elseValue
        .endIf()
        .pop();
    })
    .register(OPTIONAL_OR_ELSE_GET, (builder, call) -> {
      PsiExpression fn = call.getArgumentList().getExpressions()[0];
      builder
        .evaluateFunction(fn)
        .dup()
        .ifNull()
        .pop()
        .invokeFunction(0, fn)
        .endIf();
    })
    .register(OPTIONAL_IF_PRESENT, (builder, call) -> {
      PsiExpression fn = call.getArgumentList().getExpressions()[0];
      builder
        .evaluateFunction(fn)
        .dup()
        .ifNotNull()
        .invokeFunction(0, fn)
        .elseBranch()
        .pop()
        .pushUnknown()
        .endIf();
    });

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    BiConsumer<CFGBuilder, PsiMethodCallExpression> terminalInliner = TERMINAL_MAPPER.mapFirst(call);
    if (terminalInliner != null) {
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (!pushOptionalValue(builder, PsiUtil.skipParenthesizedExprDown(qualifierExpression), call, NullabilityProblem.callNPE)) {
        return false;
      }
      terminalInliner.accept(builder, call);
      return true;
    }
    if (pushIntermediateOperationValue(builder, call)) {
      builder.ifNotNull()
        .push(builder.getFactory().getOptionalFactory().getOptional(true))
        .elseBranch()
        .push(builder.getFactory().getOptionalFactory().getOptional(false))
        .endIf();
      return true;
    }
    return false;
  }

  @Contract("null -> null")
  private static PsiType getOptionalElementType(PsiExpression expression) {
    if (expression == null) return null;
    return PsiUtil.substituteTypeParameter(expression.getType(), JAVA_UTIL_OPTIONAL, 0, false);
  }

  private static boolean pushOptionalValue(CFGBuilder builder, PsiExpression expression,
                                           PsiExpression dereferenceContext, NullabilityProblem problem) {
    PsiType optionalElementType = getOptionalElementType(expression);
    if (optionalElementType == null) return false;
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression)expression;
      if (OPTIONAL_OF.test(qualifierCall)) {
        inlineOf(builder, optionalElementType, qualifierCall);
        builder.assignTo(builder.createTempVariable(optionalElementType));
        return true;
      }
      if (OPTIONAL_EMPTY.test(qualifierCall)) {
        builder.pushNull();
        return true;
      }
      if (pushIntermediateOperationValue(builder, qualifierCall)) {
        builder.assignTo(builder.createTempVariable(optionalElementType));
        return true;
      }
    }
    DfaOptionalValue presentOptional = builder.getFactory().getOptionalFactory().getOptional(true);
    builder
      .pushExpression(expression)
      .checkNotNull(dereferenceContext, problem)
      .push(presentOptional)
      .ifCondition(JavaTokenType.INSTANCEOF_KEYWORD)
      .push(builder.getFactory().createTypeValue(optionalElementType, Nullness.NOT_NULL))
      .elseBranch()
      .pushNull()
      .endIf()
      .assignTo(builder.createTempVariable(optionalElementType));
    return true;
  }

  private static boolean pushIntermediateOperationValue(CFGBuilder builder, PsiMethodCallExpression call) {
    boolean isFilter = OPTIONAL_FILTER.test(call);
    boolean isMap = OPTIONAL_MAP.test(call);
    boolean isFlatMap = OPTIONAL_FLAT_MAP.test(call);
    boolean isOr = OPTIONAL_OR.test(call);
    if (!isFilter && !isMap && !isFlatMap && !isOr) return false;
    PsiExpression argument = call.getArgumentList().getExpressions()[0];
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (!pushOptionalValue(builder, PsiUtil.skipParenthesizedExprDown(qualifierExpression), call, NullabilityProblem.callNPE)) return false;
    if (isFlatMap) {
      inlineFlatMap(builder, argument);
    }
    else if (isFilter) {
      inlineFilter(builder, argument);
    }
    else if (isMap) {
      inlineMap(builder, argument);
    }
    else {
      inlineOr(builder, argument);
    }
    return true;
  }

  private static void invokeAndUnwrapOptional(CFGBuilder builder,
                                              int argCount,
                                              PsiExpression function) {
    PsiLambdaExpression lambda = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(function), PsiLambdaExpression.class);
    if (lambda != null) {
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      PsiExpression lambdaBody = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (parameters.length == argCount && lambdaBody != null) {
        StreamEx.ofReversed(parameters).forEach(p -> builder.assignTo(p).pop());
        if (pushOptionalValue(builder, lambdaBody, lambdaBody, NullabilityProblem.callNPE)) {
          return;
        }
      }
    }
    builder
      .pushExpression(function)
      .checkNotNull(function, NullabilityProblem.passingNullableToNotNullParameter)
      .pop()
      .pushUnknown();
  }

  private static void inlineFlatMap(CFGBuilder builder,
                                    PsiExpression function) {
    builder
      .dup()
      .ifNotNull();
    invokeAndUnwrapOptional(builder, 1, function);
    builder.endIf();
  }

  private static void inlineOr(CFGBuilder builder,
                                    PsiExpression function) {
    builder
      .dup()
      .ifNull()
      .pop();
    invokeAndUnwrapOptional(builder, 0, function);
    builder.endIf();
  }

  private static void inlineMap(CFGBuilder builder, PsiExpression function) {
    builder
      .evaluateFunction(function)
      .dup()
      .ifNotNull()
      .invokeFunction(1, function)
      .endIf();
  }

  private static void inlineFilter(CFGBuilder builder, PsiExpression function) {
    builder
      .evaluateFunction(function)
      .dup()
      .ifNotNull()
      .dup()
      .invokeFunction(1, function)
      .ifConditionIs(false)
      .pop()
      .pushNull()
      .endIf()
      .endIf();
  }

  private static void inlineOf(CFGBuilder builder, PsiType optionalElementType, PsiMethodCallExpression qualifierCall) {
    PsiExpression argument = qualifierCall.getArgumentList().getExpressions()[0];
    builder.pushExpression(argument)
      .boxUnbox(argument, optionalElementType)
      .pushUnknown() // ... arg, ?
      .splice(2, 1, 0, 1) // ... arg, ?, arg
      .invoke(qualifierCall) // ... arg, opt -- keep original call in CFG so some warnings like "ofNullable for null" can work
      .pop(); // ... arg
    if ("of".equals(qualifierCall.getMethodExpression().getReferenceName())) {
      builder.dup()
        .ifNull()
        .pop()
        .pushUnknown()
        .endIf();
    }
  }
}
