/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class ShiftOutOfRangeInspection extends BaseInspection {

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    LongRangeSet range = (LongRangeSet)infos[0];
    Long val = range.getConstantValue();
    if (val == null) {
      return InspectionGadgetsBundle.message(
        "shift.operation.by.inappropriate.constant.problem.descriptor.out.of.bounds", range.toString());
    }
    if (val > 0) {
      return InspectionGadgetsBundle.message(
        "shift.operation.by.inappropriate.constant.problem.descriptor.too.large", val);
    }
    return InspectionGadgetsBundle.message(
      "shift.operation.by.inappropriate.constant.problem.descriptor.negative", val);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    Long val = ((LongRangeSet)infos[0]).getConstantValue();
    if (val == null) return null;
    return new ShiftOutOfRangeFix(val, ((Boolean)infos[1]).booleanValue());
  }

  private static class ShiftOutOfRangeFix extends PsiUpdateModCommandQuickFix {
    private final long myValue;
    private final boolean myLong;

    ShiftOutOfRangeFix(long value, boolean isLong) {
      this.myValue = value;
      this.myLong = isLong;
    }

    @Override
    public @NotNull String getName() {
      final int newValue = (int)(myValue & (myLong ? 63 : 31));
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myValue, newValue);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("shift.out.of.range.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiBinaryExpression binaryExpression = ObjectUtils.tryCast(element.getParent(), PsiBinaryExpression.class);
      if (binaryExpression == null) return;
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) return;
      final PsiExpression lhs = binaryExpression.getLOperand();
      int mask = PsiTypes.longType().equals(lhs.getType()) ? 63 : 31;
      final String text = String.valueOf(myValue & mask);
      new CommentTracker().replaceAndRestoreComments(rhs, text);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ShiftOutOfRange();
  }

  private static class ShiftOutOfRange extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      if (!JavaBinaryOperations.SHIFT_OPS.contains(tokenType)) return;
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) return;
      final PsiType expressionType = expression.getType();
      if (expressionType == null) return;
      LongRangeSet allowedRange;
      if (expressionType.equals(PsiTypes.longType())) {
        allowedRange = LongRangeSet.range(0, Long.SIZE - 1);
      } else if(expressionType.equals(PsiTypes.intType())) {
        allowedRange = LongRangeSet.range(0, Integer.SIZE - 1);
      } else {
        return;
      }
      LongRangeSet actualRange = CommonDataflow.getExpressionRange(rhs);
      if (actualRange != null && !actualRange.isEmpty() && !actualRange.intersects(allowedRange)) {
        registerError(sign, actualRange, expressionType.equals(PsiTypes.longType()));
      }
    }
  }
}