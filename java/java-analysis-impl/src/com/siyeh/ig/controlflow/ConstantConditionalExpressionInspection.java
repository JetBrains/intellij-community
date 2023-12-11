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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class ConstantConditionalExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiConditionalExpression expression = (PsiConditionalExpression)infos[0];
    return InspectionGadgetsBundle.message("constant.conditional.expression.problem.descriptor",
      calculateReplacementExpression(expression).getText());
  }

  @NotNull
  static PsiExpression calculateReplacementExpression(@NotNull PsiConditionalExpression exp) {
    final PsiExpression thenExpression = exp.getThenExpression();
    final PsiExpression elseExpression = exp.getElseExpression();
    final PsiExpression condition = exp.getCondition();
    assert thenExpression != null;
    assert elseExpression != null;
    return BoolUtils.isTrue(condition) ? thenExpression : elseExpression;
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ConstantConditionalFix();
  }

  private static class ConstantConditionalFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiConditionalExpression expression = (PsiConditionalExpression)startElement;
      CommentTracker ct = new CommentTracker();
      final PsiExpression replacement = calculateReplacementExpression(expression);
      PsiType type = replacement.getType();
      PsiType expressionType = expression.getType();
      if (type != null &&
          expressionType != null &&
          !type.equals(expressionType) &&
          PsiTypesUtil.isDenotableType(expressionType, expression)) {
        PsiTypeCastExpression castExpression = (PsiTypeCastExpression)ct
          .replaceAndRestoreComments(expression, "(" + expressionType.getCanonicalText() + ")" + 
                                                 ct.text(replacement, PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE));
        if (RedundantCastUtil.isCastRedundant(castExpression)) {
          RemoveRedundantCastUtil.removeCast(castExpression);
        }
      }
      else {
        ct.replaceAndRestoreComments(expression, replacement);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantConditionalExpressionVisitor();
  }

  private static class ConstantConditionalExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      @NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression condition = expression.getCondition();
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      if (elseExpression == null) {
        return;
      }
      if (BoolUtils.isFalse(condition) || BoolUtils.isTrue(condition)) {
        registerError(expression, expression);
      }
    }
  }
}