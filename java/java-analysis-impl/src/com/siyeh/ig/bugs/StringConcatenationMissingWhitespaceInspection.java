// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class StringConcatenationMissingWhitespaceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreNonStringLiterals = true;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonStringLiterals", InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationMissingWhitespaceVisitor();
  }

  private class StringConcatenationMissingWhitespaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType) || !ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final boolean formatCall = FormatUtils.isFormatCallArgument(expression);
      final PsiExpression[] operands = expression.getOperands();
      PsiExpression lhs = operands[0];
      boolean stringSeen = ExpressionUtils.hasStringType(lhs);
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression rhs = operands[i];
        stringSeen |= ExpressionUtils.hasStringType(rhs);
        if (stringSeen && isMissingWhitespace(lhs, rhs, formatCall)) {
          final PsiJavaToken token = expression.getTokenBeforeOperand(rhs);
          if (token != null) {
            registerError(token);
          }
        }
        lhs = rhs;
      }
    }

    private boolean isMissingWhitespace(PsiExpression lhs, PsiExpression rhs, boolean formatCall) {
      final @NonNls String lhsLiteral = computeStringValue(lhs);
      if (lhsLiteral != null) {
        final int length = lhsLiteral.length();
        if (length == 0 || formatCall && lhsLiteral.endsWith("%n")) {
          return false;
        }
        final char c = lhsLiteral.charAt(length - 1);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals && ExpressionUtils.hasStringType(lhs)) {
        return false;
      }
      final @NonNls String rhsLiteral = computeStringValue(rhs);
      if (rhsLiteral != null) {
        if (rhsLiteral.isEmpty() || formatCall && rhsLiteral.startsWith("%n")) {
          return false;
        }
        final char c = rhsLiteral.charAt(0);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals && ExpressionUtils.hasStringType(rhs)) {
        return false;
      }
      return true;
    }

    public @Nullable String computeStringValue(@Nullable PsiExpression expression) {
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      return value == null ? null : value.toString();
    }
  }
}
