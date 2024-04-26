// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class NegatedEqualityExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("negated.equality.expression.problem.descriptor", infos[0]);
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new NegatedEqualityExpressionFix();
  }

  private static class NegatedEqualityExpressionFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.equality.expression.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression prefixExpression)) {
        return;
      }
      if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
      if (!(operand instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      CommentTracker commentTracker = new CommentTracker();
      StringBuilder text = new StringBuilder(commentTracker.text(binaryExpression.getLOperand()));
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        text.append("!=");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        text.append("==");
      }
      else {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs != null) {
        text.append(commentTracker.text(rhs));
      }

      PsiReplacementUtil.replaceExpression(prefixExpression, text.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedEqualsVisitor();
  }

  private static class NegatedEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (!(operand instanceof PsiBinaryExpression binaryExpression)) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        registerError(expression.getOperationSign(), "==");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        registerError(expression.getOperationSign(), "!=");
      }
    }
  }
}
