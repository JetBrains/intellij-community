/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.psi.JavaTokenType.*;

public final class PointlessBitwiseExpressionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreExpressionsContainingConstants = true;

  static final @NotNull TokenSet bitwiseTokens = TokenSet.create(AND, OR, XOR, LTLT, GTGT, GTGTGT);

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final String replacementExpression = calculateReplacementExpression(expression, new CommentTracker());
    return InspectionGadgetsBundle.message(
      "expression.can.be.replaced.problem.descriptor",
      replacementExpression);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreExpressionsContainingConstants", InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option")));
  }

  String calculateReplacementExpression(PsiExpression expression, CommentTracker ct) {
    if (expression instanceof PsiPolyadicExpression) {
      return calculateReplacementExpression((PsiPolyadicExpression)expression, ct);
    }
    PsiExpression complemented = unwrapComplement(expression);
    if (complemented != null) {
      PsiExpression decremented = extractDecrementedValue(complemented);
      if (decremented != null) {
        return "-" + ct.text(decremented, ParenthesesUtils.PREFIX_PRECEDENCE);
      }
      PsiExpression twiceComplemented = unwrapComplement(complemented);
      if (twiceComplemented != null) {
        return ct.text(twiceComplemented);
      }
    }
    return "";
  }

  @NonNls
  String calculateReplacementExpression(PsiPolyadicExpression expression, CommentTracker ct) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression[] operands = expression.getOperands();
    PsiExpression previousOperand = null;
    for (int i = 0, length = operands.length; i < length; i++) {
      final PsiExpression operand = operands[i];
      if (isZero(operand)) {
        if (tokenType.equals(AND) || JavaBinaryOperations.SHIFT_OPS.contains(tokenType) && previousOperand == null) {
          return getText(expression, operands[0], operands[length - 1], PsiTypes.longType().equals(expression.getType()) ? "0L" : "0", ct);
        }
        else if (tokenType.equals(OR) || tokenType.equals(XOR) ||
                 JavaBinaryOperations.SHIFT_OPS.contains(tokenType) && previousOperand != null) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand, ct);
        }
      }
      else if (isAllOnes(operand)) {
        if (tokenType.equals(AND)) {
          return getText(expression, i == length - 1 ? expression.getTokenBeforeOperand(operand) : operand, ct);
        }
        if (tokenType.equals(OR)) {
          return ct.text(operand);
        }
        else if (tokenType.equals(XOR)) {
          if (previousOperand != null) {
            return getText(expression, previousOperand, operand, getTildeReplacement(previousOperand, ct), ct);
          }
          else {
            final PsiExpression nextOperand = operands[i + 1];
            return getText(expression, operand, nextOperand, getTildeReplacement(nextOperand, ct), ct);
          }
        }
      }
      else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(previousOperand, operand)) {
        if (tokenType.equals(OR) || tokenType.equals(AND)) {
          return getText(expression, previousOperand, operand, ct.text(operand), ct);
        }
        else if (tokenType.equals(XOR)) {
          return getText(expression, previousOperand, operand, PsiTypes.longType().equals(expression.getType()) ? "0L" : "0", ct);
        }
      }
      else {
        PsiExpression left = optionallyUnwrapComplement(previousOperand);
        PsiExpression right = optionallyUnwrapComplement(operand);
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, right)) {
          if (tokenType.equals(AND)) {
            return getText(expression, previousOperand, operand, PsiTypes.longType().equals(expression.getType()) ? "0L" : "0", ct);
          } else if (tokenType.equals(OR) || tokenType.equals(XOR)) {
            return getText(expression, previousOperand, operand, PsiTypes.longType().equals(expression.getType()) ? "-1L" : "-1", ct);
          }
        }
      }
      previousOperand = operand;
    }
    return "";
  }

  private static String getTildeReplacement(PsiExpression operand, CommentTracker ct) {
    PsiExpression decrementedValue = extractDecrementedValue(operand);
    if (decrementedValue != null) {
      return "-" + ct.text(decrementedValue, ParenthesesUtils.PREFIX_PRECEDENCE);
    }
    return "~" + ct.text(operand, ParenthesesUtils.PREFIX_PRECEDENCE);
  }

  private static PsiExpression extractDecrementedValue(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiBinaryExpression binOp) {
      if (binOp.getOperationTokenType().equals(MINUS)) {
        Number right = JavaPsiMathUtil.getNumberFromLiteral(binOp.getROperand());
        if ((right instanceof Integer || right instanceof Long) && right.longValue() == 1L) {
          return binOp.getLOperand();
        }
      }
    }
    return null;
  }

  private static String getText(PsiPolyadicExpression expression, PsiElement fromTarget, PsiElement untilTarget,
                                @NotNull @NonNls String replacement, CommentTracker ct) {
    final StringBuilder result = new StringBuilder();
    boolean stop = false;
    for (PsiElement child = expression.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child == fromTarget) {
        stop = true;
        result.append(replacement);
      }
      else if (child == untilTarget) {
        stop = false;
      }
      else if (child instanceof PsiComment && !result.isEmpty() || !stop) {
        result.append(ct.text(child));
      }
      else if (child instanceof PsiJavaToken && untilTarget == null) {
        stop = false;
      }
    }
    return result.toString();
  }

  private static String getText(PsiPolyadicExpression expression, PsiElement exclude, CommentTracker ct) {
    return getText(expression, exclude, null, "", ct).trim();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBitwiseVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new PointlessBitwiseFix();
  }

  private class PointlessBitwiseFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "pointless.bitwise.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiExpression expression = (PsiExpression)startElement;
      CommentTracker ct = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, ct);
      if(!newExpression.isEmpty()) {
        ct.replaceAndRestoreComments(expression, newExpression);
      }
    }
  }

  private class PointlessBitwiseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      PsiExpression complemented = unwrapComplement(expression);
      if (complemented == null) return;
      if (extractDecrementedValue(complemented) != null) {
        registerError(expression, expression);
      } else {
        PsiExpression twiceComplemented = unwrapComplement(complemented);
        if (twiceComplemented != null && unwrapComplement(twiceComplemented) == null) {
          // In case of triple or more complements report innermost only to avoid overlapping reports
          registerError(expression, expression);
        }
      }
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType sign = expression.getOperationTokenType();
      if (!bitwiseTokens.contains(sign)) {
        return;
      }
      if (PsiUtilCore.hasErrorElementChild(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand == null) {
          return;
        }
        final PsiType type = operand.getType();
        if (type == null || type.equals(PsiTypes.booleanType()) ||
            type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
          return;
        }
      }
      final boolean isPointless;
      if (sign.equals(AND) || sign.equals(OR) || sign.equals(XOR)) {
        isPointless = booleanExpressionIsPointless(operands);
      }
      else if (JavaBinaryOperations.SHIFT_OPS.contains(sign)) {
        isPointless = shiftExpressionIsPointless(operands);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean booleanExpressionIsPointless(PsiExpression[] operands) {
      PsiExpression previousExpression = null;
      for (PsiExpression operand : operands) {
        if (isZero(operand) || isAllOnes(operand) ||
            (areEquivalentModuloComplement(previousExpression, operand) && !SideEffectChecker.mayHaveSideEffects(operand))) {
          return true;
        }
        previousExpression = operand;
      }
      return false;
    }

    private static boolean areEquivalentModuloComplement(PsiExpression op1, PsiExpression op2) {
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
        optionallyUnwrapComplement(op1), optionallyUnwrapComplement(op2));
    }

    private boolean shiftExpressionIsPointless(PsiExpression[] operands) {
      for (PsiExpression operand : operands) {
        if (isZero(operand)) {
          return true;
        }
      }
      return false;
    }
  }

  private static PsiExpression optionallyUnwrapComplement(PsiExpression op) {
    PsiExpression unwrapped = unwrapComplement(op);
    return unwrapped == null ? op : unwrapped;
  }

  private static PsiExpression unwrapComplement(PsiExpression op) {
    op = PsiUtil.skipParenthesizedExprDown(op);
    if (op instanceof PsiPrefixExpression && ((PsiPrefixExpression)op).getOperationTokenType().equals(TILDE)) {
      return ((PsiPrefixExpression)op).getOperand();
    }
    return null;
  }

  private boolean isZero(PsiExpression expression) {
    if (m_ignoreExpressionsContainingConstants
        && !(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    return ExpressionUtils.isZero(expression);
  }

  private boolean isAllOnes(PsiExpression expression) {
    final PsiType expressionType = expression.getType();
    final Object value;
    if (m_ignoreExpressionsContainingConstants) {
      value = JavaPsiMathUtil.getNumberFromLiteral(expression);
    }
    else {
      value = ConstantExpressionUtil.computeCastTo(expression, expressionType);
    }
    return (value instanceof Integer || value instanceof Short || value instanceof Byte) && ((Number)value).intValue() == -1 ||
           value instanceof Long && ((Long)value).longValue() == 0xffffffffffffffffL;
  }
}