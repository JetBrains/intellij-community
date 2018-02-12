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

/**
 * Created by Roman Ivanov.
 */
public class OperationReductionMigration extends BaseStreamApiMigration {
  private final ReductionOperation myReductionOperation;

  protected OperationReductionMigration(boolean shouldWarn,
                                        ReductionOperation context) {
    super(shouldWarn, "reduce()");
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

    if(type.equals(PsiType.BOOLEAN)) {
      type = PsiType.BOOLEAN.getBoxedType(body); // hack to avoid .map(b -> b) when boxing needed
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
    private final Predicate<PsiExpression> myInitializerReplaceCondition;
    private final Predicate<PsiVariable> myAccumulatorRestriction;
    private final String myIdentity;
    private final String myOperation;


    public ReductionOperation(IElementType compoundAssignmentOp,
                              Predicate<PsiExpression> initializerReplaceCondition,
                              Predicate<PsiVariable> accumulatorRestriction,
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

    public Predicate<PsiExpression> getInitializerExpressionRestriction() {
      return myInitializerReplaceCondition;
    }

    public String getIdentity() {
      return myIdentity;
    }

    public String getOperation() {
      return myOperation;
    }

    public Predicate<PsiVariable> getAccumulatorRestriction() {
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
           && (variable.getType().equals(PsiType.INT) || variable.getType().equals(PsiType.LONG));
  }

  static boolean arithmeticTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType() instanceof PsiPrimitiveType && !variable.getType().equals(PsiType.FLOAT);
  }

  private static boolean booleanTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType().equalsToText("boolean") || variable.getType().equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN);
  }
}
