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
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class DivideByZeroInspection extends BaseInspection {


  @SuppressWarnings("PublicField")
  public boolean reportMayBeZero = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportMayBeZero",
               InspectionGadgetsBundle.message("divide.by.zero.problem.may.option"))
        .description(InspectionGadgetsBundle.message("divide.by.zero.problem.may.option.description")));
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull String getID() {
    return "divzero";
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    if (infos.length > 1 && infos[1] instanceof ThreeState threeState && threeState == ThreeState.UNSURE) {
      return InspectionGadgetsBundle.message("divide.by.zero.problem.may.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("divide.by.zero.problem.descriptor");
    }
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    if (infos.length > 0 && infos[0] instanceof PsiBinaryExpression binOp) {
      if (binOp.getOperationTokenType().equals(JavaTokenType.DIV) && isZero(binOp.getLOperand()) == ThreeState.YES) {
        PsiType type = binOp.getType();
        if (PsiTypes.doubleType().equals(type) || PsiTypes.floatType().equals(type)) {
          return new ReplaceWithNaNFix();
        }
      }
    }
    if (infos.length > 1 && infos[1] instanceof ThreeState threeState && threeState == ThreeState.UNSURE) {
      return LocalQuickFix.from(new UpdateInspectionOptionFix(
        this, "reportMayBeZero",
        InspectionGadgetsBundle.message("divide.by.zero.problem.may.option.disabled", false), false));
    }
    return null;
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new DivisionByZeroVisitor();
  }

  private class DivisionByZeroVisitor extends BaseInspectionVisitor {

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
        ThreeState zero = isZero(operand);
        if (zero != ThreeState.NO) {
          registerError(operand, expression, zero);
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
      if (!tokenType.equals(JavaTokenType.DIVEQ) && !tokenType.equals(JavaTokenType.PERCEQ) || isZero(rhs) != ThreeState.YES) {
        return;
      }
      registerError(expression);
    }
  }

  private ThreeState isZero(PsiExpression expression) {
    final Object value = ConstantExpressionUtil.computeCastTo(expression, PsiTypes.doubleType());
    if (value instanceof Double) {
      final double constantValue = ((Double)value).doubleValue();
      return constantValue == 0.0 ? ThreeState.YES : ThreeState.NO;
    }
    DfType dfType = CommonDataflow.getDfType(expression);
    Number val = dfType.getConstantOfType(Number.class);
    if (val != null) {
      return val.doubleValue() == 0.0 ? ThreeState.YES : ThreeState.NO;
    }
    if(!reportMayBeZero) return ThreeState.NO;
    if (dfType instanceof DfIntType dfIntType) {
      LongRangeSet range = dfIntType.getRange();
      List<LongRangeSet> ranges = range.asRanges();
      if (ranges.size() < 4 &&
          ContainerUtil.exists(ranges, t ->
            t.getConstantValue() != null && t.getConstantValue() == 0L ||
            t.max() - t.min() < 2 && t.contains(0L)) &&
          //some heuristic that doesn't have infinite boundaries
          !ContainerUtil.or(ranges, t ->
            t.max() - t.min() > Integer.MAX_VALUE / 100 ||
            t.contains(Integer.MAX_VALUE) ||
            t.contains(Integer.MIN_VALUE))) {
        return ThreeState.UNSURE;
      }
    }
    return ThreeState.NO;
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

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "NaN");
    }
  }
}