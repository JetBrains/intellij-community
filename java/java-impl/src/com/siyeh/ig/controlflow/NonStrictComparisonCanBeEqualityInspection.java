// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.TrackingRunner;
import com.intellij.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class NonStrictComparisonCanBeEqualityInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression binOp) {
        IElementType token = binOp.getOperationTokenType();
        RelationType relation = DfaPsiUtil.getRelationByToken(token);
        if (relation != RelationType.GE && relation != RelationType.LE) return;
        PsiExpression lOperand = binOp.getLOperand();
        PsiExpression rOperand = binOp.getROperand();
        if (rOperand == null) return;
        PsiType lType = lOperand.getType();
        PsiType rType = rOperand.getType();
        if (!(lType instanceof PsiPrimitiveType) || !(rType instanceof PsiPrimitiveType)) return;
        if (!TypeConversionUtil.isIntegralNumberType(lType) || !TypeConversionUtil.isIntegralNumberType(rType)) return;
        if (PsiUtil.isConstantExpression(binOp)) return;
        CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(binOp);
        if (result == null) return;
        DfIntegralType leftDfType = ObjectUtils.tryCast(result.getDfType(lOperand), DfIntegralType.class);
        if (leftDfType == null) return;
        DfIntegralType rightDfType = ObjectUtils.tryCast(result.getDfType(rOperand), DfIntegralType.class);
        if (rightDfType == null) return;
        if (leftDfType instanceof DfConstantType && rightDfType instanceof DfConstantType) return;
        if (rightDfType instanceof DfConstantType) {
          DfType leftNarrow = leftDfType.meetRelation(relation, rightDfType);
          if (leftNarrow.equals(rightDfType)) {
            holder.registerProblem(
              binOp.getOperationSign(), InspectionGadgetsBundle.message("inspection.non.strict.comparison.equality.message"),
              new ReplaceWithEqualityFix(),
              new FindDfaProblemCauseFix(false, lOperand,
                                         new TrackingRunner.RangeDfaProblemType(leftDfType.getRange(), (PsiPrimitiveType)lType)));
          }
        }
        else if (leftDfType instanceof DfConstantType) {
          DfType rightNarrow = rightDfType.meetRelation(Objects.requireNonNull(relation.getFlipped()), leftDfType);
          if (rightNarrow.equals(leftDfType)) {
            holder.registerProblem(
              binOp.getOperationSign(), InspectionGadgetsBundle.message("inspection.non.strict.comparison.equality.message"),
              new ReplaceWithEqualityFix(),
              new FindDfaProblemCauseFix(false, rOperand,
                                         new TrackingRunner.RangeDfaProblemType(rightDfType.getRange(), (PsiPrimitiveType)rType)));
          }
        }
      }
    };
  }

  private static class ReplaceWithEqualityFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "==");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement token, @NotNull ModPsiUpdater updater) {
      PsiBinaryExpression expression = ObjectUtils.tryCast(token.getParent(), PsiBinaryExpression.class);
      if (expression == null) return;
      String text = expression.getText();
      TextRange range = token.getTextRangeInParent();
      String result = text.substring(0, range.getStartOffset()) + "==" + text.substring(range.getEndOffset());
      expression.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(result, null));
    }
  }
}
