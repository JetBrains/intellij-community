/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ExtractMethodFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class OverlyComplexArithmeticExpressionInspection extends BaseInspection {

  private static final TokenSet arithmeticTokens =
    TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC);
  private static final int TERM_LIMIT = 6;

  /**
   * @noinspection PublicField
   */
  public int m_limit = TERM_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message("overly.complex.arithmetic.expression.max.number.option"), 2,
             100));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExtractMethodFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("overly.complex.arithmetic.expression.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyComplexArithmeticExpressionVisitor();
  }

  private class OverlyComplexArithmeticExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (!isArithmetic(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpression && isArithmetic((PsiExpression)parent)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= m_limit) {
        return;
      }
      if (ExpressionUtils.isOnlyExpressionInMethod(expression)) {
        return;
      }
      registerError(expression);
    }

    private int countTerms(PsiExpression expression) {
      if (!isArithmetic(expression)) {
        return 1;
      }
      if (expression instanceof PsiPolyadicExpression poly) {
        int count = 0;
        for (PsiExpression operand : poly.getOperands()) {
          count += countTerms(operand);
        }
        return count;
      }
      else if (expression instanceof PsiPrefixExpression prefixExpression) {
        final PsiExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      }
      else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
        final PsiExpression contents = parenthesizedExpression.getExpression();
        return countTerms(contents);
      }
      return 1;
    }

    private static boolean isArithmetic(PsiExpression expression) {
      if (expression instanceof PsiPolyadicExpression binaryExpression) {
        final PsiType type = expression.getType();
        if (TypeUtils.isJavaLangString(type)) {
          return false; //ignore string concatenations
        }
        return arithmeticTokens.contains(binaryExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiPrefixExpression prefixExpression) {
        return arithmeticTokens.contains(prefixExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
        final PsiExpression contents = parenthesizedExpression.getExpression();
        return isArithmetic(contents);
      }
      return false;
    }
  }
}