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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class NonShortCircuitBooleanInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "NonShortCircuitBooleanExpression";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new NonShortCircuitBooleanFix();
  }

  private static class NonShortCircuitBooleanFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("non.short.circuit.boolean.expression.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiPolyadicExpression polyadic) {
        doReplacePolyadicExpression(polyadic);
      }
      else if (element instanceof PsiAssignmentExpression assignment) {
        PsiAssignmentExpression replaced = PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression(assignment);
        PsiExpression expression = replaced.getRExpression();
        if (expression instanceof PsiPolyadicExpression polyadic) {
          doReplacePolyadicExpression(polyadic);
        }
      }
    }

    private static void doReplacePolyadicExpression(PsiPolyadicExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      final String operandText = getShortCircuitOperand(tokenType);
      final PsiExpression[] operands = expression.getOperands();
      final StringBuilder newExpression = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      for (PsiExpression operand : operands) {
        if (!newExpression.isEmpty()) {
          newExpression.append(operandText);
        }
        newExpression.append(commentTracker.text(operand));
      }
      PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
    }

    private static String getShortCircuitOperand(IElementType tokenType) {
      if (tokenType.equals(JavaTokenType.AND)) {
        return "&&";
      }
      else {
        return "||";
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonShortCircuitBooleanVisitor();
  }

  private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.AND) && !tokenType.equals(JavaTokenType.OR)) {
        return;
      }
      doCheck(expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ANDEQ) && !tokenType.equals(JavaTokenType.OREQ)) {
        return;
      }
      doCheck(expression);
    }

    private void doCheck(PsiExpression expression) {
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiTypes.booleanType())) {
        return;
      }
      registerError(expression);
    }
  }
}