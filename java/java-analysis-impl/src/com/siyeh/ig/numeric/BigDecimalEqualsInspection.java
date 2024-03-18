/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class BigDecimalEqualsInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("big.decimal.equals.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new BigDecimalEqualsFix();
  }

  private static class BigDecimalEqualsFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "compareTo()==0");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(startElement, PsiMethodCallExpression.class);
      final EqualityCheck check = EqualityCheck.from(call);
      if (check == null) return;
      final CommentTracker commentTracker = new CommentTracker();
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      final String qualifierText = commentTracker.text(left, ParenthesesUtils.METHOD_CALL_PRECEDENCE);
      final String argText = commentTracker.text(right);
      @NonNls String replacement = qualifierText + ".compareTo(" + argText + ")==0";
      if (!check.isLeftDereferenced() && NullabilityUtil.getExpressionNullability(left, true) != Nullability.NOT_NULL) {
        final boolean bothNullShouldEqual = call.getArgumentList().getExpressionCount() == 2 &&
                                            NullabilityUtil.getExpressionNullability(right, true) != Nullability.NOT_NULL;
        replacement = bothNullShouldEqual
                      ? left.getText() + "==null?" + right.getText() + "==null:" + replacement
                      : left.getText() + "!=null && " + replacement;
      }
      PsiReplacementUtil.replaceExpression(call, replacement, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BigDecimalEqualsVisitor();
  }

  private static class BigDecimalEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final EqualityCheck check = EqualityCheck.from(expression);
      if (check == null) return;
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      if (!ExpressionUtils.hasType(left, "java.math.BigDecimal")) return;
      if (!ExpressionUtils.hasType(right, "java.math.BigDecimal")) return;
      if (ExpressionUtils.isVoidContext(expression)) {
        // otherwise the quickfix will produce uncompilable code (out of merely incorrect code).
        return;
      }
      registerMethodCallError(expression);
    }
  }
}