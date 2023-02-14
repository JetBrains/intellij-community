// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaOptionalSupport;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

import static com.intellij.codeInspection.util.OptionalUtil.*;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * An inliner which is capable to inline some Optional chains like
 * {@code Optional.of(xyz).map(lambda).filter(lambda).flatMap(lambda).orElseGet(lambda)}
 */
public class OptionalChainInliner implements CallInliner {

  private static final CallMatcher OPTIONAL_OR_ELSE = anyOf(
    instanceCall(JAVA_UTIL_OPTIONAL, "orElse").parameterCount(1),
    instanceCall(OPTIONAL_INT, "orElse").parameterCount(1),
    instanceCall(OPTIONAL_LONG, "orElse").parameterCount(1),
    instanceCall(OPTIONAL_DOUBLE, "orElse").parameterCount(1),
    instanceCall(GUAVA_OPTIONAL, "or").parameterTypes("T"));
  private static final CallMatcher OPTIONAL_GET = anyOf(
    exactInstanceCall(JAVA_UTIL_OPTIONAL, "get", "orElseThrow").parameterCount(0),
    exactInstanceCall(OPTIONAL_INT, "getAsInt", "orElseThrow").parameterCount(0),
    exactInstanceCall(OPTIONAL_LONG, "getAsLong", "orElseThrow").parameterCount(0),
    exactInstanceCall(OPTIONAL_DOUBLE, "getAsDouble", "orElseThrow").parameterCount(0),
    instanceCall(GUAVA_OPTIONAL, "get").parameterCount(0)
  );
  private static final CallMatcher OPTIONAL_OR_NULL = instanceCall(GUAVA_OPTIONAL, "orNull").parameterCount(0);
  private static final CallMatcher OPTIONAL_OR_ELSE_GET = anyOf(
    instanceCall(JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1),
    instanceCall(OPTIONAL_INT, "orElseGet").parameterCount(1),
    instanceCall(OPTIONAL_LONG, "orElseGet").parameterCount(1),
    instanceCall(OPTIONAL_DOUBLE, "orElseGet").parameterCount(1),
    instanceCall(GUAVA_OPTIONAL, "or").parameterTypes("com.google.common.base.Supplier"));
  private static final CallMatcher OPTIONAL_OR = instanceCall(JAVA_UTIL_OPTIONAL, "or").parameterCount(1); // Java 9
  private static final CallMatcher OPTIONAL_IF_PRESENT = anyOf(
    instanceCall(JAVA_UTIL_OPTIONAL, "ifPresent").parameterCount(1),
    instanceCall(OPTIONAL_INT, "ifPresent").parameterCount(1),
    instanceCall(OPTIONAL_LONG, "ifPresent").parameterCount(1),
    instanceCall(OPTIONAL_DOUBLE, "ifPresent").parameterCount(1));
  private static final CallMatcher OPTIONAL_FILTER = instanceCall(JAVA_UTIL_OPTIONAL, "filter").parameterCount(1);
  private static final CallMatcher OPTIONAL_MAP = instanceCall(JAVA_UTIL_OPTIONAL, "map").parameterCount(1);
  // Guava transform() throws if function returns null, so handled separately
  private static final CallMatcher GUAVA_TRANSFORM = instanceCall(GUAVA_OPTIONAL, "transform").parameterCount(1);
  private static final CallMatcher OPTIONAL_FLAT_MAP = instanceCall(JAVA_UTIL_OPTIONAL, "flatMap").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF = anyOf(
    staticCall(JAVA_UTIL_OPTIONAL, "of", "ofNullable").parameterCount(1),
    staticCall(OPTIONAL_INT, "of").parameterCount(1),
    staticCall(OPTIONAL_LONG, "of").parameterCount(1),
    staticCall(OPTIONAL_DOUBLE, "of").parameterCount(1),
    staticCall(GUAVA_OPTIONAL, "of", "fromNullable").parameterCount(1));
  private static final CallMatcher OPTIONAL_EMPTY = anyOf(
    staticCall(JAVA_UTIL_OPTIONAL, "empty").parameterCount(0),
    staticCall(OPTIONAL_INT, "empty").parameterCount(0),
    staticCall(OPTIONAL_LONG, "empty").parameterCount(0),
    staticCall(OPTIONAL_DOUBLE, "empty").parameterCount(0),
    staticCall(GUAVA_OPTIONAL, "absent").parameterCount(0));
  private static final CallMatcher GUAVA_TO_JAVA =
    instanceCall(GUAVA_OPTIONAL, "toJavaUtil").parameterCount(0);

  private static final CallMapper<BiConsumer<CFGBuilder, PsiMethodCallExpression>> TERMINAL_MAPPER =
    new CallMapper<BiConsumer<CFGBuilder, PsiMethodCallExpression>>()
      .register(OPTIONAL_OR_ELSE, (builder, call) -> {
        PsiExpression argument = call.getArgumentList().getExpressions()[0];
        // orElse(null) is a no-op
        if (ExpressionUtils.isNullLiteral(argument)) {
          builder.resultOf(call);
          return;
        }
        builder.pushExpression(argument) // stack: .. optValue, elseValue
          .boxUnbox(argument, call.getType())
          .splice(2, 0, 1, 1) // stack: .. elseValue, optValue, optValue
          .ifNotNull()
          .boxUnbox(call, getOptionalElementType(call.getMethodExpression().getQualifierExpression(), true), call.getType())
          .swap() // stack: .. optValue, elseValue
          .end()
          .pop()
          .resultOf(call);
      })
      .register(OPTIONAL_GET, (builder, call) -> {
        builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), "java.util.NoSuchElementException")
          .boxUnbox(call, getOptionalElementType(call.getMethodExpression().getQualifierExpression(), true), call.getType())
          .resultOf(call);
      })
      .register(OPTIONAL_OR_NULL, CFGBuilder::resultOf)
      .register(OPTIONAL_OR_ELSE_GET, (builder, call) -> {
        PsiExpression fn = call.getArgumentList().getExpressions()[0];
        builder
          .evaluateFunction(fn)
          .dup()
          .ifNull()
          .pop()
          .invokeFunction(0, fn)
          .elseBranch()
          .boxUnbox(call, getOptionalElementType(call.getMethodExpression().getQualifierExpression(), true), call.getType())
          .end()
          .resultOf(call);
      })
      .register(OPTIONAL_IF_PRESENT, (builder, call) -> {
        PsiExpression fn = call.getArgumentList().getExpressions()[0];
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        builder
          .evaluateFunction(fn)
          .dup()
          .ifNotNull()
          .boxUnbox(call, getOptionalElementType(qualifier, true), getOptionalElementType(qualifier, false))
          .invokeFunction(1, fn)
          .elseBranch()
          .pop()
          .pushUnknown()
          .end()
          .resultOf(call);
      });

  private static final CallMapper<BiConsumer<CFGBuilder, PsiExpression>> INTERMEDIATE_MAPPER =
    new CallMapper<BiConsumer<CFGBuilder, PsiExpression>>()
      .register(OPTIONAL_MAP, (builder, function) -> inlineMap(builder, function, Nullability.NULLABLE))
      .register(GUAVA_TRANSFORM, (builder, function) -> inlineMap(builder, function, Nullability.NOT_NULL))
      .register(OPTIONAL_FILTER, (builder, function) -> builder
        .evaluateFunction(function)
        .dup()
        .ifNotNull()
        .dup()
        .invokeFunction(1, function)
        .ifConditionIs(false)
        .pop()
        .pushNull()
        .end()
        .end())
      .register(OPTIONAL_FLAT_MAP, (builder, function) -> builder
        .dup()
        .ifNotNull()
        .chain(b -> invokeAndUnwrapOptional(b, 1, function))
        .end())
      .register(OPTIONAL_OR, (builder, function) -> builder
        .dup()
        .ifNull()
        .pop()
        .chain(b -> invokeAndUnwrapOptional(b, 0, function))
        .end())
      .register(GUAVA_TO_JAVA, (builder, stub) -> {/* no op */});

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    BiConsumer<CFGBuilder, PsiMethodCallExpression> terminalInliner = TERMINAL_MAPPER.mapFirst(call);
    if (terminalInliner != null) {
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (!pushOptionalValue(builder, PsiUtil.skipParenthesizedExprDown(qualifierExpression), null)) {
        return false;
      }
      terminalInliner.accept(builder, call);
      return true;
    }
    if (pushIntermediateOperationValue(builder, call)) {
      DfaVariableValue result = builder.createTempVariable(call.getType());
      builder
        .assign(result, DfTypes.typedObject(call.getType(), Nullability.NOT_NULL)) // stack: ...value opt
        .push(SpecialField.OPTIONAL_VALUE.createValue(builder.getFactory(), result)) // stack: ...value opt opt.value
        .splice(3, 1, 0, 2)
        .assign()
        .pop()
        .resultOf(call);
      return true;
    }
    if (OPTIONAL_EMPTY.test(call)) {
      builder.push(DfaOptionalSupport.getOptionalValue(false), call);
      return true;
    }
    return false;
  }

  @Contract("null, _ -> null")
  private static PsiType getOptionalElementType(PsiExpression expression, boolean box) {
    if (expression == null) return null;
    PsiClassType type = ObjectUtils.tryCast(expression.getType(), PsiClassType.class);
    if (type == null) return null;
    String rawName = type.rawType().getCanonicalText();
    if (rawName.equals(OPTIONAL_INT)) {
      return box ? PsiTypes.intType().getBoxedType(expression) : PsiTypes.intType();
    }
    if (rawName.equals(OPTIONAL_LONG)) {
      return box ? PsiTypes.longType().getBoxedType(expression) : PsiTypes.longType();
    }
    if (rawName.equals(OPTIONAL_DOUBLE)) {
      return box ? PsiTypes.doubleType().getBoxedType(expression) : PsiTypes.doubleType();
    }
    if (!rawName.equals(JAVA_UTIL_OPTIONAL) && !rawName.equals(GUAVA_OPTIONAL)) return null;
    PsiType[] parameters = type.getParameters();
    if (parameters.length != 1) return null;
    return parameters[0];
  }

  private static boolean pushOptionalValue(CFGBuilder builder, PsiExpression expression,
                                           NullabilityProblemKind<? super PsiExpression> problem) {
    PsiType optionalElementType = getOptionalElementType(expression, true);
    if (optionalElementType == null) return false;
    if (expression instanceof PsiMethodCallExpression qualifierCall) {
      if (OPTIONAL_EMPTY.test(qualifierCall)) {
        builder.pushNull();
        return true;
      }
      if (pushIntermediateOperationValue(builder, qualifierCall)) {
        builder
          .dup()
          .wrap(DfTypes.typedObject(qualifierCall.getType(), Nullability.NOT_NULL), SpecialField.OPTIONAL_VALUE)
          .resultOf(qualifierCall)
          .pop()
          .assignTo(builder.createTempVariable(optionalElementType));
        return true;
      }
    }
    builder
      .pushExpression(expression, problem)
      .unwrap(SpecialField.OPTIONAL_VALUE)
      .assignTo(builder.createTempVariable(optionalElementType));
    return true;
  }

  private static boolean pushIntermediateOperationValue(CFGBuilder builder, PsiMethodCallExpression call) {
    if (OPTIONAL_OF.test(call)) {
      PsiType optionalElementType = getOptionalElementType(call, true);
      inlineOf(builder, optionalElementType, call);
      return true;
    }
    BiConsumer<CFGBuilder, PsiExpression> intermediateInliner = INTERMEDIATE_MAPPER.mapFirst(call);
    if (intermediateInliner == null) return false;
    PsiExpression argument = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (!pushOptionalValue(builder, PsiUtil.skipParenthesizedExprDown(qualifierExpression), null)) {
      return false;
    }
    intermediateInliner.accept(builder, argument);
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
        if (pushOptionalValue(builder, lambdaBody, NullabilityProblemKind.nullableFunctionReturn)) {
          return;
        }
        // Restore stack for common invokeFunction
        StreamEx.of(parameters).map(variable -> PlainDescriptor.createVariableValue(builder.getFactory(), variable)).forEach(builder::push);
      }
    }
    builder
      .evaluateFunction(function)
      .invokeFunction(argCount, function, Nullability.NOT_NULL)
      .pop()
      .pushUnknown();
  }

  private static void inlineMap(CFGBuilder builder, PsiExpression function, Nullability resultNullability) {
    builder
      .evaluateFunction(function)
      .dup()
      .ifNotNull()
      .invokeFunction(1, function, resultNullability)
      .end();
  }

  private static void inlineOf(CFGBuilder builder, PsiType optionalElementType, PsiMethodCallExpression qualifierCall) {
    PsiExpression argument = qualifierCall.getArgumentList().getExpressions()[0];
    if ("of".equals(qualifierCall.getMethodExpression().getReferenceName())) {
      builder
        .pushExpression(argument, NullabilityProblemKind.passingToNotNullParameter)
        .boxUnbox(argument, optionalElementType)
        .push(DfaOptionalSupport.getOptionalValue(true), qualifierCall)
        .pop();
    }
    else {
      builder
        .pushExpression(argument)
        .boxUnbox(argument, optionalElementType)
        .dup()
        .ifNull()
          .push(DfaOptionalSupport.getOptionalValue(false), qualifierCall)
          .elseBranch()
          .push(DfaOptionalSupport.getOptionalValue(true), qualifierCall)
        .end()
        .pop();
    }
  }

  @Override
  public boolean mayInferPreciseType(@NotNull PsiExpression expression) {
    return InlinerUtil.isLambdaChainParameterReference(expression, TypeUtils::isOptional);
  }
}
