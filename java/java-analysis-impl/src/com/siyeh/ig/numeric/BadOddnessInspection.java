/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public final class BadOddnessInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "bad.oddness.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadOddnessVisitor();
  }

  private static class BadOddnessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (expression.getROperand() == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      final PsiExpression rhs = expression.getROperand();
      if (isModTwo(lhs) && hasValue(rhs, 1)) {
        registerError(expression, expression);
      }
      if (isModTwo(rhs) && hasValue(lhs, 1)) {
        registerError(expression, expression);
      }
    }

    private static boolean isModTwo(PsiExpression exp) {
      if (!(exp instanceof PsiBinaryExpression binary)) {
        return false;
      }
      final IElementType tokenType = binary.getOperationTokenType();
      if (!JavaTokenType.PERC.equals(tokenType)) {
        return false;
      }
      final PsiExpression rhs = binary.getROperand();
      final PsiExpression lhs = binary.getLOperand();
      if (rhs == null) {
        return false;
      }
      return hasValue(rhs, 2) && !isChanged(lhs) && canBeNegative(lhs);
    }

    private static boolean canBeNegative(PsiExpression lhs) {
      LongRangeSet range = CommonDataflow.getExpressionRange(lhs);
      return range == null || range.min() < 0;
    }
    
    private static boolean isChanged(PsiExpression lhs) {
      if (!(lhs instanceof PsiReferenceExpression)) return false;
      PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)lhs).resolve(), PsiVariable.class);
      if (variable == null) return false;
      PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (codeBlock == null) return false;
      return !HighlightControlFlowUtil.isEffectivelyFinal(variable, codeBlock, null);
    }

    private static boolean hasValue(PsiExpression expression, int testValue) {
      final Integer value = (Integer)ConstantExpressionUtil.computeCastTo(expression, PsiTypes.intType());
      return value != null && value.intValue() == testValue;
    }
  }
}