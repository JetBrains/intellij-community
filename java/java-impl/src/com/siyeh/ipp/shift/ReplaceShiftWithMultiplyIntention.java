/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.shift;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplaceShiftWithMultiplyIntention extends PsiBasedModCommandAction<PsiExpression> implements DumbAware {
  ReplaceShiftWithMultiplyIntention() {
    super(PsiExpression.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.shift.with.multiply.intention.family.name");
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiExpression element, @NotNull ActionContext context) {
    return new ShiftByLiteralPredicate().satisfiedBy(element);
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    if (element instanceof PsiAssignmentExpression exp) {
      return getPresentation(exp.getOperationSign(), JavaTokenType.GTGTEQ, exp.getLExpression(), "/=", "*=");
    } else {
      final PsiBinaryExpression exp = (PsiBinaryExpression) element;
      return getPresentation(exp.getOperationSign(), JavaTokenType.GTGT, exp.getLOperand(), "/", "*");
    }
  }

  private static @Nls @NotNull Presentation getPresentation(
    @NotNull PsiJavaToken sign,
    @NotNull IElementType tokenType,
    @NotNull PsiExpression lExpr,
    @NotNull String divOperator,
    @NotNull String mulOperator
  ) {
    String message;
    if (sign.getTokenType().equals(tokenType)) {
      if (PsiUtil.getLanguageLevel(lExpr).isLessThan(LanguageLevel.JDK_1_8)) {
        message = CommonQuickFixBundle.message("fix.replace.x.with.y.may.change.semantics", sign.getText(), divOperator);
      } else {
        if (isSafelyDivisible(lExpr)) {
          message = CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), divOperator);
        } else {
          message = QuickFixBundle.message("fix.replace.x.with.division", sign.getText());
        }
      }
    } else {
      message = CommonQuickFixBundle.message("fix.replace.x.with.y", sign.getText(), mulOperator);
    }
    return Presentation.of(message);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiExpression element) {
    if (element instanceof PsiBinaryExpression expr) {
      return replaceShiftWithMultiplyOrDivide(expr);
    }
    else {
      PsiAssignmentExpression expr = (PsiAssignmentExpression)element;
      return replaceShiftAssignWithMultiplyOrDivideAssign(expr);
    }
  }

  private static boolean isSafelyDivisible(@NotNull PsiExpression lhsExpr) {
    LongRangeSet range = CommonDataflow.getExpressionRange(lhsExpr);
    return range != null && range.min() >= 0;
  }

  private static ModCommand replaceShiftAssignWithMultiplyOrDivideAssign(@NotNull PsiAssignmentExpression expr) {
    final PsiExpression rhsExpr = PsiUtil.skipParenthesizedExprDown(expr.getRExpression());
    if (!(rhsExpr instanceof PsiLiteralExpression rhsLiteral)) return ModCommand.nop();
    final String rhsText = rhsReplacement(rhsLiteral, expr.getLExpression().getType());
    if (expr.getOperationTokenType().equals(JavaTokenType.GTGTEQ)) {
      if (isSafelyDivisible(expr.getLExpression()) || PsiUtil.getLanguageLevel(expr).isLessThan(LanguageLevel.JDK_1_8)) {
        return ModCommand.psiUpdate(expr, e -> replaceAssignmentExprOperator(e, "/=", rhsText));
      } else {
        return ModCommand.chooseAction(
          CommonQuickFixBundle.message("fix.replace.with"),
          ModCommand.psiUpdateStep(
            expr,
            CommonQuickFixBundle.message("fix.replace.x.with.y", ">>=", "Math.floorDiv"),
            (e, updater) -> {
              CommentTracker commentTracker = new CommentTracker();
              final String lhsText = commentTracker.text(e.getLExpression(), ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
              PsiReplacementUtil.replaceExpression(e, lhsText + "=" + "Math.floorDiv(" + lhsText + ", " + rhsText + ")");
            }),
          ModCommand.psiUpdateStep(
            expr,
            CommonQuickFixBundle.message("fix.replace.x.with.y.may.change.semantics", ">>=", "/="),
            (e, updater) -> replaceAssignmentExprOperator(e, "/=", rhsText)
          )
        );
      }
    } else {
      return ModCommand.psiUpdate(expr, e -> replaceAssignmentExprOperator(e, "*=", rhsText));
    }
  }

  private static void replaceAssignmentExprOperator(PsiAssignmentExpression e, String x, String rhsText) {
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(e.getLExpression(), ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
    PsiReplacementUtil.replaceExpression(e, lhsText + x + rhsText, commentTracker);
  }

  private static ModCommand replaceShiftWithMultiplyOrDivide(@NotNull PsiBinaryExpression expr) {
    final PsiExpression rhsExpr = PsiUtil.skipParenthesizedExprDown(expr.getROperand());
    if (!(rhsExpr instanceof PsiLiteralExpression rhsLiteral)) return ModCommand.nop();
    final String rhsText = rhsReplacement(rhsLiteral, expr.getLOperand().getType());
    if (expr.getOperationTokenType().equals(JavaTokenType.GTGT)) {
      if (isSafelyDivisible(expr.getLOperand()) || PsiUtil.getLanguageLevel(expr).isLessThan(LanguageLevel.JDK_1_8)) {
        return ModCommand.psiUpdate(expr, e -> replaceBinaryExprOperator(e, "/", rhsText));
      } else {
        return ModCommand.chooseAction(
          CommonQuickFixBundle.message("fix.replace.with"),
          ModCommand.psiUpdateStep(
            expr,
            CommonQuickFixBundle.message("fix.replace.x.with.y", ">>", "Math.floorDiv"),
            (e, updater) -> {
              CommentTracker commentTracker = new CommentTracker();
              final String lhsText = commentTracker.text(e.getLOperand(), ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
              PsiReplacementUtil.replaceExpression(e, parenthesizeIfRequired(e, "Math.floorDiv(" + lhsText + ", " + rhsText + ")"), commentTracker);
            }),
          ModCommand.psiUpdateStep(
            expr,
            CommonQuickFixBundle.message("fix.replace.x.with.y.may.change.semantics", ">>", "/"),
            (e, updater) -> replaceBinaryExprOperator(e, "/", rhsText)
          )
        );
      }
    } else {
      return ModCommand.psiUpdate(expr, e -> replaceBinaryExprOperator(e, "*", rhsText));
    }
  }

  private static void replaceBinaryExprOperator(PsiBinaryExpression e, String x, String rhsText) {
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(e.getLOperand(), ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
    PsiReplacementUtil.replaceExpression(e, parenthesizeIfRequired(e, lhsText + x + rhsText), commentTracker);
  }

  private static String parenthesizeIfRequired(@NotNull PsiExpression expression, @NotNull String replacement) {
    if (expression.getParent() instanceof PsiExpression parent && !(parent instanceof PsiParenthesizedExpression) &&
        ParenthesesUtils.getPrecedence(parent) < ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
      return '(' + replacement + ')';
    }
    return replacement;
  }

  private static String rhsReplacement(@NotNull PsiLiteralExpression rhs, @Nullable PsiType type) {
    final Number value = (Number)rhs.getValue();
    assert value != null;
     if (PsiTypes.longType().equals(type)) {
       return Long.toString(1L << value.intValue()) + 'L';
     } else {
       return Integer.toString(1 << value.intValue());
     }
  }
}