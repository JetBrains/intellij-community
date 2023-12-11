/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DivideByZeroInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "divzero";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("divide.by.zero.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length > 0 && infos[0] instanceof PsiBinaryExpression binOp) {
      if (binOp.getOperationTokenType().equals(JavaTokenType.DIV) && isZero(binOp.getLOperand())) {
        PsiType type = binOp.getType();
        if (PsiTypes.doubleType().equals(type) || PsiTypes.floatType().equals(type)) {
          return new ReplaceWithNaNFix();
        }
      }
    }
    return null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DivisionByZeroVisitor();
  }

  private static class DivisionByZeroVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.DIV.equals(tokenType) && !JavaTokenType.PERC.equals(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (isZero(operand)) {
          registerError(operand, expression);
          return;
        }
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = expression.getRExpression();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIVEQ) && !tokenType.equals(JavaTokenType.PERCEQ) || !isZero(rhs)) {
        return;
      }
      registerError(expression);
    }
  }

  private static boolean isZero(PsiExpression expression) {
    final Object value = ConstantExpressionUtil.computeCastTo(expression, PsiTypes.doubleType());
    if (value instanceof Double) {
      final double constantValue = ((Double)value).doubleValue();
      return constantValue == 0.0;
    }
    DfType dfType = CommonDataflow.getDfType(expression);
    Number val = dfType.getConstantOfType(Number.class);
    return val != null && val.doubleValue() == 0.0;
  }

  private static class ReplaceWithNaNFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiBinaryExpression division = PsiTreeUtil.getNonStrictParentOfType(startElement, PsiBinaryExpression.class);
      if (division == null) return;
      PsiType type = division.getType();
      if (!(type instanceof PsiPrimitiveType)) return;
      String className = ((PsiPrimitiveType)type).getBoxedTypeName();
      new CommentTracker().replaceAndRestoreComments(division, className+".NaN");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "NaN");
    }
  }
}