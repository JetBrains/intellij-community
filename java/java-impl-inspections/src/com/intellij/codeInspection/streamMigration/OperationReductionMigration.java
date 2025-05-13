// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Predicate;

final class OperationReductionMigration extends BaseStreamApiMigration {
  private final ReductionOperation myReductionOperation;

  OperationReductionMigration(boolean shouldWarn, ReductionOperation context) {
    super(shouldWarn, "reduce");
    myReductionOperation = context;
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = StreamApiMigrationInspection.extractAccumulator(assignment, myReductionOperation.getCompoundAssignmentOp());
    if (var == null) return null;

    CommentTracker ct = new CommentTracker();
    PsiExpression operand = StreamApiMigrationInspection.extractOperand(assignment, myReductionOperation.getCompoundAssignmentOp());
    if (operand == null) return null;
    PsiType type = var.getType();

    PsiType operandType = operand.getType();
    if (operandType != null && !TypeConversionUtil.isAssignable(type, operandType)) {
      operand = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ct.text(operand, ParenthesesUtils.TYPE_CAST_PRECEDENCE), operand);
    }
    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
    String leftOperand = javaStyle.suggestUniqueVariableName("a", body, true);
    String rightOperand = javaStyle.suggestUniqueVariableName("b", body, true);

    if(type.equals(PsiTypes.booleanType())) {
      type = PsiTypes.booleanType().getBoxedType(body); // hack to avoid .map(b -> b) when boxing needed
    }

    PsiExpression initializer = var.getInitializer();
    String identity = initializer != null && myReductionOperation.getInitializerExpressionRestriction().test(initializer)
               ? ct.text(initializer)
               : myReductionOperation.getIdentity();
    String stream = tb.add(new StreamApiMigrationInspection.MapOp(operand, tb.getVariable(), type)).generate(ct)
                    + String.format(Locale.ENGLISH, ".reduce(%s, (%s, %s) -> %s %s %s)",
                                    identity, leftOperand, rightOperand, leftOperand,
                                    myReductionOperation.getOperation(), rightOperand);
    return replaceWithOperation(tb.getStreamSourceStatement(), var, stream, type, myReductionOperation, ct);
  }

  static class ReductionOperation {
    private final IElementType myCompoundAssignmentOp;
    private final Predicate<? super PsiExpression> myInitializerReplaceCondition;
    private final Predicate<? super PsiVariable> myAccumulatorRestriction;
    private final String myIdentity;
    private final String myOperation;


    ReductionOperation(IElementType compoundAssignmentOp,
                              Predicate<? super PsiExpression> initializerReplaceCondition,
                              Predicate<? super PsiVariable> accumulatorRestriction,
                              String identity,
                              String operation) {
      myCompoundAssignmentOp = compoundAssignmentOp;
      myInitializerReplaceCondition = initializerReplaceCondition;
      myAccumulatorRestriction = accumulatorRestriction;
      myIdentity = identity;
      myOperation = operation;
    }

    public IElementType getCompoundAssignmentOp() {
      return myCompoundAssignmentOp;
    }

    Predicate<? super PsiExpression> getInitializerExpressionRestriction() {
      return myInitializerReplaceCondition;
    }

    public String getIdentity() {
      return myIdentity;
    }

    public String getOperation() {
      return myOperation;
    }

    Predicate<? super PsiVariable> getAccumulatorRestriction() {
      return myAccumulatorRestriction;
    }
  }

  static final ReductionOperation SUM_OPERATION = new ReductionOperation(
    JavaTokenType.PLUSEQ,
    ExpressionUtils::isZero,
    OperationReductionMigration::arithmeticTypeRestriction,
    "0",
    "+"
  );

  static final ReductionOperation[] OPERATIONS = {
    new ReductionOperation(
      JavaTokenType.ASTERISKEQ,
      ExpressionUtils::isOne,
      OperationReductionMigration::arithmeticTypeRestriction,
      "1",
      "*"
    ),
    new ReductionOperation(
      JavaTokenType.ANDEQ,
      expression -> Boolean.TRUE.equals(ExpressionUtils.computeConstantExpression(expression)),
      OperationReductionMigration::booleanTypeRestriction,
      "true",
      "&&"
    ),
    new ReductionOperation(
      JavaTokenType.OREQ,
      expression -> Boolean.FALSE.equals(ExpressionUtils.computeConstantExpression(expression)),
      OperationReductionMigration::booleanTypeRestriction,
      "false",
      "||"
    ),
    new ReductionOperation(
      JavaTokenType.OREQ,
      ExpressionUtils::isZero,
      OperationReductionMigration::arithmeticTypeRestriction,
      "0",
      "|"
    ),
    new ReductionOperation(
      JavaTokenType.ANDEQ,
      OperationReductionMigration::isMinusOne,
      OperationReductionMigration::bitwiseTypeRestriction,
      "-1",
      "&"
    ),
    new ReductionOperation(
      JavaTokenType.XOREQ,
      ExpressionUtils::isZero,
      OperationReductionMigration::bitwiseTypeRestriction,
      "0",
      "^"
    )
  };

  private static boolean isMinusOne(PsiExpression expression) {
    Object constant = ExpressionUtils.computeConstantExpression(expression);
    if(constant == null) {
      return false;
    }
    return (constant instanceof Integer || constant instanceof Long) && ((Number)constant).longValue() == -1;
  }

  static boolean bitwiseTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType() instanceof PsiPrimitiveType
           && (variable.getType().equals(PsiTypes.intType()) || variable.getType().equals(PsiTypes.longType()));
  }

  static boolean arithmeticTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType() instanceof PsiPrimitiveType && !variable.getType().equals(PsiTypes.floatType());
  }

  private static boolean booleanTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType().equalsToText("boolean") || variable.getType().equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN);
  }
}
