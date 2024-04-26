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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class AssignmentUsedAsConditionInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.used.as.condition.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new AssignmentUsedAsConditionFix();
  }

  private static class AssignmentUsedAsConditionFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", "=", "==");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
      final PsiExpression leftExpression = expression.getLExpression();
      final PsiExpression rightExpression = expression.getRExpression();
      assert rightExpression != null;
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = commentTracker.text(leftExpression) + "==" + commentTracker.text(rightExpression);
      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentUsedAsConditionVisitor();
  }

  private static class AssignmentUsedAsConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null ||
          expression.getOperationTokenType() != JavaTokenType.EQ ||
          !PsiTypes.booleanType().equals(expression.getType())) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression) && !(lhs instanceof PsiArrayAccessExpression)) {
        return;
      }
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      if (!PsiUtil.isCondition(expression, parent)) {
        return;
      }
      registerError(expression);
    }
  }
}