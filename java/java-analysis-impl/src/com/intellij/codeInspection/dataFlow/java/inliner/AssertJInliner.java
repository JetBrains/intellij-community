// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_ASSERTION_ERROR;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;

public class AssertJInliner implements CallInliner {
  private static final String[] METHOD_NAMES =
    {"isNotNull", "isNull", "isPresent", "isNotEmpty", "isNotBlank", "isNotPresent", "isEmpty", "isTrue", "isFalse",
      "contains", "containsSame", "containsInstanceOf", "hasOnlyElementsOfType",
      "hasOnlyElementsOfTypes", "have", "haveAtLeast", "haveAtLeastOne", "haveAtMost", "haveExactly", "hasSize", "hasSizeBetween",
      "hasSizeGreaterThan", "hasSizeLessThan", "hasSizeGreaterThanOrEqualTo", "hasSizeLessThanOrEqualTo"};
  private static final CallMatcher ASSERT = CallMatcher.anyOf(
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", METHOD_NAMES),
    CallMatcher.instanceCall("com.google.common.truth.Subject", METHOD_NAMES)
  );
  private static final CallMatcher INTERMEDIATE = CallMatcher.anyOf(
    CallMatcher.instanceCall("org.assertj.core.api.Descriptable", "describedAs", "as"),
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", "describedAs", "as", "withFailMessage", "overridingErrorMessage"),
    CallMatcher.instanceCall("org.assertj.core.api.Assert", "withRepresentation", "withThreadDumpOnError")
  );

  private static final CallMatcher ASSERT_THAT = CallMatcher.anyOf(
    CallMatcher.staticCall("org.assertj.core.api.BDDAssertions", "then").parameterCount(1),
    CallMatcher.staticCall("org.assertj.core.api.Assertions", "assertThat").parameterCount(1),
    CallMatcher.instanceCall("org.assertj.core.api.WithAssertions", "assertThat").parameterCount(1),
    CallMatcher.staticCall("com.google.common.truth.Truth", "assertThat").parameterCount(1),
    CallMatcher.staticCall("com.google.common.truth.StandardSubjectBuilder", "that").parameterCount(1)
  );

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!ASSERT.matches(call)) return false;
    List<PsiExpression> intermediateArgs = new ArrayList<>();
    PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
    while (INTERMEDIATE.matches(qualifier)) {
      intermediateArgs.addAll(0, Arrays.asList(qualifier.getArgumentList().getExpressions()));
      qualifier = MethodCallUtils.getQualifierMethodCall(qualifier);
    }
    if (!ASSERT_THAT.matches(qualifier)) return false;
    PsiMethod method = call.resolveMethod();
    if (method == null) return false;
    PsiExpression valueToCheck = qualifier.getArgumentList().getExpressions()[0];
    builder.pushExpression(valueToCheck);
    for (PsiExpression arg : intermediateArgs) {
      builder.pushExpression(arg);
    }
    builder.splice(intermediateArgs.size());
    PsiExpression[] args = call.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      builder.pushExpression(arg);
    }
    builder.splice(args.length);
    PsiType type = valueToCheck.getType();
    SpecialField field = SpecialField.fromQualifierType(DfTypes.typedObject(type, Nullability.UNKNOWN));
    // Note that `new ContractFailureProblem(call)` cannot be extracted to a variable here, as its identity is necessary
    // for analysis (see DataFlowInstructionVisitor#myFailingCalls)
    boolean container = field == SpecialField.COLLECTION_SIZE || field == SpecialField.ARRAY_LENGTH ||
                        field == SpecialField.STRING_LENGTH || field == SpecialField.OPTIONAL_VALUE;
    String methodName = method.getName();
    switch (methodName) {
      case "isNotNull", "have", "haveAtLeast", "haveAtLeastOne", "haveAtMost",
        "haveExactly", "hasOnlyElementsOfType", "hasOnlyElementsOfTypes" ->
        builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
      case "isNull" -> builder.ensure(RelationType.EQ, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
      case "isPresent", "isNotEmpty", "isNotBlank", "contains", "containsSame", "containsInstanceOf" -> {
        builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        if (container) {
          boolean mayCheckNothing = methodName.startsWith("contains") && args.length == 0 ||
                                    (args.length == 1 && method.isVarArgs() && !MethodCallUtils.isVarArgCall(call));
          if (!mayCheckNothing) {
            builder.unwrap(field);
            builder.ensure(RelationType.NE, field == SpecialField.OPTIONAL_VALUE ? DfTypes.NULL : DfTypes.intValue(0),
                           new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
          }
        }
      }
      case "isNotPresent", "isEmpty" -> {
        builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        if (container) {
          builder.unwrap(field);
          builder.ensure(RelationType.EQ, field == SpecialField.OPTIONAL_VALUE ? DfTypes.NULL : DfTypes.intValue(0),
                         new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        }
      }
      case "isTrue" -> {
        if (PsiTypes.booleanType().equals(type) || TypeUtils.typeEquals(JAVA_LANG_BOOLEAN, type)) {
          builder
            .boxUnbox(valueToCheck, PsiTypes.booleanType())
            .ensure(RelationType.EQ, DfTypes.TRUE, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        }
        else if (type instanceof PsiClassType) {
          builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        }
      }
      case "isFalse" -> {
        if (PsiTypes.booleanType().equals(type) || TypeUtils.typeEquals(JAVA_LANG_BOOLEAN, type)) {
          builder
            .boxUnbox(valueToCheck, PsiTypes.booleanType())
            .ensure(RelationType.EQ, DfTypes.FALSE, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        }
        else if (type instanceof PsiClassType) {
          builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        }
      }
      case "hasSize" -> sizeLimit(builder, call, field, RelationType.EQ);
      case "hasSizeGreaterThan" -> sizeLimit(builder, call, field, RelationType.GT);
      case "hasSizeLessThan" -> sizeLimit(builder, call, field, RelationType.LT);
      case "hasSizeGreaterThanOrEqualTo" -> sizeLimit(builder, call, field, RelationType.GE);
      case "hasSizeLessThanOrEqualTo" -> sizeLimit(builder, call, field, RelationType.LE);
      case "hasSizeBetween" -> {
        builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
        if ((field == SpecialField.COLLECTION_SIZE || field == SpecialField.ARRAY_LENGTH) && args.length == 2) {
          Integer min = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(args[0]), Integer.class);
          Integer max = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(args[1]), Integer.class);
          builder.unwrap(field);
          if (min != null) {
            builder.ensure(RelationType.GE, DfTypes.intValue(min), new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
          }
          if (max != null) {
            builder.ensure(RelationType.LE, DfTypes.intValue(max), new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
          }
        }
      }
    }
    builder.pop().pushUnknown();
    return true;
  }

  private static void sizeLimit(@NotNull CFGBuilder builder,
                                @NotNull PsiMethodCallExpression call,
                                @Nullable SpecialField field,
                                @NotNull RelationType relation) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    builder.ensure(RelationType.NE, DfTypes.NULL, new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
    if ((field == SpecialField.COLLECTION_SIZE || field == SpecialField.ARRAY_LENGTH) && args.length == 1) {
      Integer expectedSize = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(args[0]), Integer.class);
      if (expectedSize != null) {
        builder.unwrap(field);
        builder.ensure(relation, DfTypes.intValue(expectedSize), new ContractFailureProblem(call), JAVA_LANG_ASSERTION_ERROR);
      }
    }
  }
}
