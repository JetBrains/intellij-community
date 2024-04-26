/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.types.DfNullConstantType;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil.isEffectivelyFinal;
import static com.intellij.psi.JavaTokenType.*;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprDown;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprUp;
import static com.intellij.util.ObjectUtils.tryCast;

public final class NumberEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "number.comparison.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NumberEqualityVisitor();
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return EqualityToEqualsFix.buildEqualityFixes((PsiBinaryExpression)infos[0]);
  }

  private static class NumberEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (!hasNumberType(rhs)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!hasNumberType(lhs)) {
        return;
      }
      if (isUniqueConstant(rhs) || isUniqueConstant(lhs)) return;
      DfNullConstantType leftConstant = tryCast(CommonDataflow.getDfType(lhs), DfNullConstantType.class);
      DfNullConstantType rightConstant = tryCast(CommonDataflow.getDfType(rhs), DfNullConstantType.class);
      DfaNullability leftNullability = leftConstant != null ? leftConstant.getNullability() : null;
      DfaNullability rightNullability = rightConstant != null ? rightConstant.getNullability() : null;
      if (leftNullability == DfaNullability.NULL || rightNullability == DfaNullability.NULL) return;
      Filter filter = new Filter(lhs, rhs);
      if (isOneOfVariablesDefinitelyNullAsPartOfBooleanExpression(expression, filter)) return;
      final PsiElement element = PsiTreeUtil.findFirstParent(expression, filter);
      final boolean isOneOfVariablesDefinitelyNull = element != null;
      if (isOneOfVariablesDefinitelyNull) return;
      registerError(expression.getOperationSign(), expression);
    }

    private static boolean isUniqueConstant(PsiExpression expression) {
      PsiReferenceExpression ref = tryCast(skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
      if (ref == null) return false;
      PsiField target = tryCast(ref.resolve(), PsiField.class);
      if (target == null) return false;
      if (target instanceof PsiEnumConstant) return true;
      if (!(target instanceof PsiFieldImpl)) return false;
      if (!target.hasModifierProperty(PsiModifier.STATIC) || !target.hasModifierProperty(PsiModifier.FINAL)) return false;
      PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(target);
      return ExpressionUtils.isNewObject(initializer);
    }

    private static boolean hasNumberType(PsiExpression expression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_NUMBER);
    }
  }

  private static final class Filter implements Condition<PsiElement> {
    private final PsiExpression lhs;
    private final PsiExpression rhs;

    private Filter(final PsiExpression lhs, final PsiExpression rhs) {
      this.lhs = lhs;
      this.rhs = rhs;
    }

    @Override
    public boolean value(final PsiElement element) {
      PsiConditionalExpression ternary = tryCast(element, PsiConditionalExpression.class);
      if (ternary != null) {
        final PsiExpression condition = ternary.getCondition();
        if (isOneOfVariablesDefinitelyNull(ternary, condition)) return true;
      }

      final PsiIfStatement ifStatement = tryCast(element, PsiIfStatement.class);
      if (ifStatement == null) return false;

      final PsiExpression condition = ifStatement.getCondition();
      return isOneOfVariablesDefinitelyNull(ifStatement, condition);
    }

    public boolean isOneOfVariablesDefinitelyNull(PsiElement element, PsiExpression condition) {
      condition = skipParenthesizedExprDown(condition);
      if (condition == null) return false;

      boolean isNegated = false;
      PsiBinaryExpression binOp = tryCast(condition, PsiBinaryExpression.class);
      if (binOp == null && BoolUtils.isNegation(condition)) {
        final PsiExpression operand = skipParenthesizedExprDown(((PsiPrefixExpression) condition).getOperand());
        binOp = tryCast(operand, PsiBinaryExpression.class);
        isNegated = true;
      }
      if (binOp == null) return false;

      final IElementType tokenType = binOp.getOperationTokenType();
      final PsiBinaryExpression lOperand = tryCast(skipParenthesizedExprDown(binOp.getLOperand()), PsiBinaryExpression.class);
      final PsiBinaryExpression rOperand = tryCast(skipParenthesizedExprDown(binOp.getROperand()), PsiBinaryExpression.class);
      if (lOperand == null || rOperand == null) return false;

      final PsiExpression lValue = ExpressionUtils.getValueComparedWithNull(lOperand);
      final PsiExpression rValue = ExpressionUtils.getValueComparedWithNull(rOperand);
      if (lValue == null || rValue == null) return false;

      final IElementType lTokenType = lOperand.getOperationTokenType();
      final IElementType rTokenType = rOperand.getOperationTokenType();
      boolean areBothOperandsComparedWithNull = lTokenType == EQEQ && rTokenType == EQEQ;
      boolean areBothOperandsComparedWithNotNull = lTokenType == NE && rTokenType == NE;
      if (areBothOperandsComparedWithNull == areBothOperandsComparedWithNotNull) return false;
      List<IElementType> targetTokenTypes = areBothOperandsComparedWithNull ? Arrays.asList(OROR, OR) : Arrays.asList(ANDAND, AND);
      if (!targetTokenTypes.contains(tokenType)) return false;

      final PsiElement elementContainsNumberEquality;
      if (element instanceof PsiBinaryExpression) {
        elementContainsNumberEquality = getAnotherOperand((PsiBinaryExpression)element, condition);
      }
      else {
        elementContainsNumberEquality = getElementMustContainNumberEquality(element, isNegated, areBothOperandsComparedWithNull);
      }
      if (!PsiTreeUtil.isAncestor(elementContainsNumberEquality, lhs, false)) return false;

      final EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();

      final PsiReferenceExpression lReference = tryCast(skipParenthesizedExprDown(lhs), PsiReferenceExpression.class);
      if (lReference == null) return false;
      final PsiReferenceExpression rReference = tryCast(skipParenthesizedExprDown(rhs), PsiReferenceExpression.class);
      if (rReference == null) return false;

      final PsiVariable lVariable = tryCast(lReference.resolve(), PsiVariable.class);
      if (lVariable == null) return false;
      final PsiVariable rVariable = tryCast(rReference.resolve(), PsiVariable.class);
      if (rVariable == null) return false;

      final boolean isEffectivelyFinal = isEffectivelyFinal(lVariable, elementContainsNumberEquality, null) &&
                                         isEffectivelyFinal(rVariable, elementContainsNumberEquality, null);
      if (!isEffectivelyFinal) return false;

      return (checker.expressionsMatch(lhs, lValue).isExactMatch() && checker.expressionsMatch(rhs, rValue).isExactMatch()) ||
             (checker.expressionsMatch(lhs, rValue).isExactMatch() && checker.expressionsMatch(rhs, lValue).isExactMatch());
    }
  }

  private static PsiElement getElementMustContainNumberEquality(PsiElement element, boolean isNegated,
                                                                boolean areBothOperandsComparedWithNull) {
    final PsiElement result;
    if (element instanceof PsiConditionalExpression ternary) {
      final PsiElement thenExpression = skipParenthesizedExprDown(ternary.getThenExpression());
      final PsiElement elseExpression = skipParenthesizedExprDown(ternary.getElseExpression());
      result = isNegated ^ areBothOperandsComparedWithNull ? thenExpression : elseExpression;
    } else if (element instanceof PsiIfStatement ifStatement) {
      final PsiElement thenBranch = ifStatement.getThenBranch();
      final PsiElement elseBranch = ifStatement.getElseBranch();
      result = isNegated ^ areBothOperandsComparedWithNull ? thenBranch : elseBranch;
    } else {
      result = null;
    }
    return result;
  }

  private static PsiExpression getAnotherOperand(@NotNull PsiBinaryExpression expression, PsiExpression operand) {
    PsiExpression rOperand = skipParenthesizedExprDown(expression.getROperand());
    return rOperand != operand ? rOperand : expression.getLOperand();
  }

  private static boolean isOneOfVariablesDefinitelyNullAsPartOfBooleanExpression(PsiBinaryExpression expression, Filter filter) {
    final PsiBinaryExpression binOp = tryCast(skipParenthesizedExprUp(expression.getParent()), PsiBinaryExpression.class);
    if (binOp == null) return false;
    return filter.isOneOfVariablesDefinitelyNull(binOp, binOp.getLOperand()) ||
           filter.isOneOfVariablesDefinitelyNull(binOp, binOp.getROperand());
  }
}