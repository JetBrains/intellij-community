// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SuspiciousIntegerDivAssignmentInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean myReportPossiblyExactDivision = true;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.integer.div.assignment.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myReportPossiblyExactDivision", JavaAnalysisBundle.message("inspection.suspicious.integer.div.assignment.option")));
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SuspiciousIntegerDivAssignmentFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousIntegerDivAssignmentVisitor();
  }

  private static class SuspiciousIntegerDivAssignmentFix extends PsiUpdateModCommandQuickFix {

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiAssignmentExpression expression = ObjectUtils.tryCast(startElement, PsiAssignmentExpression.class);
      if (expression == null) {
        return;
      }
      final PsiJavaToken token = expression.getOperationSign();
      final IElementType tokenType = token.getTokenType();
      if (!JavaTokenType.ASTERISKEQ.equals(tokenType) && !JavaTokenType.DIVEQ.equals(tokenType)) {
        return;
      }
      final PsiBinaryExpression rhs = getRhs(expression);
      if (rhs == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      final PsiExpression operand = rhs.getLOperand();
      final Number number = JavaPsiMathUtil.getNumberFromLiteral(operand);
      if (number != null) {
        PsiReplacementUtil.replaceExpression(operand, number + ".0", tracker);
      }
      else {
        PsiReplacementUtil.replaceExpression(operand, "(double)" + operand.getText(), tracker);
      }
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("suspicious.integer.div.assignment.quickfix");
    }
  }

  private class SuspiciousIntegerDivAssignmentVisitor extends BaseInspectionVisitor {
    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final IElementType assignmentTokenType = assignment.getOperationTokenType();
      if (!assignmentTokenType.equals(JavaTokenType.ASTERISKEQ) && !assignmentTokenType.equals(JavaTokenType.DIVEQ)) {
        return;
      }
      final PsiBinaryExpression rhs = getRhs(assignment);
      if (rhs == null) {
        return;
      }
      final LongRangeSet dividendRange = CommonDataflow.getExpressionRange(rhs.getLOperand());
      if (dividendRange != null) {
        final LongRangeSet divisorRange = CommonDataflow.getExpressionRange(rhs.getROperand());
        if (divisorRange != null) {
          final LongRangeSet modRange = dividendRange.mod(divisorRange);
          if (modRange.isEmpty() || LongRangeSet.point(0).equals(modRange)) {
            return; // modRange.isEmpty() could be if divisor is always zero; for this case we have another inspection, so no need to report
          }
          if (!modRange.contains(0)) {
            registerError(assignment);
            return;
          }
        }
      }
      if (!myReportPossiblyExactDivision) return;
      registerError(assignment);
    }
  }

  @Nullable
  private static PsiBinaryExpression getRhs(@NotNull PsiAssignmentExpression assignment) {
    final PsiBinaryExpression rhs =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiBinaryExpression.class);
    if (rhs == null ||
        !rhs.getOperationTokenType().equals(JavaTokenType.DIV) ||
        !TypeConversionUtil.isIntegralNumberType(rhs.getType())) {
      return null;
    }
    return rhs;
  }
}
