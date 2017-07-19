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
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Created by Roman Ivanov.
 */
public class OperationReductionMigration extends BaseStreamApiMigration {
  private OperationContext myOperationContext;

  protected OperationReductionMigration(boolean shouldWarn,
                                        OperationContext context) {
    super(shouldWarn, "reduce()");
    myOperationContext = context;
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = StreamApiMigrationInspection.extractAccumulator(assignment, myOperationContext.getCompoundAssignmentOp());
    if (var == null) return null;

    PsiExpression operand = StreamApiMigrationInspection.extractOperand(assignment, myOperationContext.getCompoundAssignmentOp());
    if (operand == null) return null;
    PsiType type = var.getType();

    PsiType operandType = operand.getType();
    if (operandType != null && !TypeConversionUtil.isAssignable(type, operandType)) {
      operand = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ParenthesesUtils.getText(operand, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE), operand);
    }
    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
    String leftOperand = javaStyle.suggestUniqueVariableName("a", body, true);
    String rightOperand = javaStyle.suggestUniqueVariableName("b", body, true);

    String stream = tb.add(new StreamApiMigrationInspection.MapOp(operand, tb.getVariable(), type)).generate()
                    + String.format(Locale.ENGLISH, ".reduce(%s, (%s, %s) -> %s %s %s)",
                                    myOperationContext.getIdentity(), leftOperand, rightOperand, leftOperand,
                                    myOperationContext.getOperation(), rightOperand);
    return replaceWithOperation(tb.getMainLoop(), var, stream, type, myOperationContext);
  }

  public static class OperationContext {
    private IElementType myCompoundAssignmentOp;
    private Predicate<PsiExpression> myInitializerInlineCondition;
    private Predicate<PsiVariable> myAccumulatorRestriction;
    private String identity;
    private String operation;


    public OperationContext(IElementType compoundAssignmentOp,
                            Predicate<PsiExpression> initializerInlineCondition,
                            Predicate<PsiVariable> accumulatorRestriction,
                            String identity,
                            String operation) {
      myCompoundAssignmentOp = compoundAssignmentOp;
      myInitializerInlineCondition = initializerInlineCondition;
      myAccumulatorRestriction = accumulatorRestriction;
      this.identity = identity;
      this.operation = operation;
    }

    public IElementType getCompoundAssignmentOp() {
      return myCompoundAssignmentOp;
    }

    public Predicate<PsiExpression> getInitializerExpressionRestriction() {
      return myInitializerInlineCondition;
    }

    public String getIdentity() {
      return identity;
    }

    public String getOperation() {
      return operation;
    }

    public Predicate<PsiVariable> getAccumulatorRestriction() {
      return myAccumulatorRestriction;
    }
  }

  static OperationContext[] operations = {
    new OperationContext(
      JavaTokenType.ASTERISKEQ,
      ExpressionUtils::isOne,
      OperationReductionMigration::arithmeticTypeRestriction,
      "1",
      "*"
    ),
    new OperationContext(
      JavaTokenType.ANDEQ,
      expression -> expression.getText().equals("true"),
      OperationReductionMigration::booleanTypeRestriction,
      "true",
      "&&"
    ),
    new OperationContext(
      JavaTokenType.OREQ,
      expression -> expression.getText().equals("false"),
      OperationReductionMigration::booleanTypeRestriction,
      "false",
      "||"
    ),
    new OperationContext(
      JavaTokenType.OREQ,
      ExpressionUtils::isZero,
      OperationReductionMigration::arithmeticTypeRestriction,
      "0",
      "|"
    ),
    new OperationContext(
      JavaTokenType.ANDEQ,
      expression -> expression.getText().equals("-1"),
      OperationReductionMigration::bitwiseTypeRestriction,
      "-1",
      "&"
    ),
    new OperationContext(
      JavaTokenType.XOREQ,
      ExpressionUtils::isZero,
      OperationReductionMigration::bitwiseTypeRestriction,
      "0",
      "^"
    )
  };

  static boolean bitwiseTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType() instanceof PsiPrimitiveType
           && !variable.getType().equalsToText("float")
           && !variable.getType().equalsToText("double");
  }

  static boolean arithmeticTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType() instanceof PsiPrimitiveType && !variable.getType().equalsToText("float");
  }

  private static boolean booleanTypeRestriction(@NotNull PsiVariable variable) {
    return variable.getType().equalsToText("boolean") || variable.getType().equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN);
  }
}
