// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ThreadLocalSetWithNullInspection extends BaseInspection {

  private final CallMatcher THREAD_LOCAL_SET =
    CallMatcher.instanceCall("java.lang.ThreadLocal", "set").parameterCount(1);

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.local.set.with.null.problem.descriptor");
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new ReplaceWithRemove();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(
        @NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        if (!THREAD_LOCAL_SET.test(expression)) {
          return;
        }
        PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (arguments.length != 1) {
          return;
        }
        PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
        if (firstArgument == null) {
          return;
        }
        if (!(firstArgument instanceof PsiLiteralExpression literalExpression)) {
          return;
        }
        if (!ExpressionUtils.isNullLiteral(literalExpression)) {
          return;
        }
        PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null) {
          return;
        }
        registerMethodCallError(expression);
      }
    };
  }

  private static class ReplaceWithRemove extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("thread.local.set.with.null.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent.getParent();
      PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(qualifierExpression);
      final String newExpressionText = qualifierExpression.getText() + ".remove()";
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpressionText, tracker);
    }
  }
}
