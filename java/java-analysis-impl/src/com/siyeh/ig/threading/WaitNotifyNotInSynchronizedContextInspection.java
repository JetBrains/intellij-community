// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class WaitNotifyNotInSynchronizedContextInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final String text = (String)infos[0];
    return InspectionGadgetsBundle.message("wait.notify.while.not.synchronized.on.problem.descriptor", text);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaiNotifyNotInSynchronizedContextVisitor();
  }

  private static class WaiNotifyNotInSynchronizedContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression) &&
          !ThreadingUtils.isWaitCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        if (isSynchronizedOnThis(expression) || isCoveredByGuardedByAnnotation(expression, "this")) {
          return;
        }
        registerError(expression, JavaKeywords.THIS);
      }
      else if (qualifier instanceof PsiReferenceExpression) {
        if (isSynchronizedOn(expression, qualifier)) {
          return;
        }
        final String text = qualifier.getText();
        if (isCoveredByGuardedByAnnotation(expression, text)) {
          return;
        }
        registerError(expression, text);
      }
    }

    private static boolean isCoveredByGuardedByAnnotation(PsiElement context, String guard) {
      final PsiMember member = PsiTreeUtil.getParentOfType(context, PsiMember.class);
      if (member == null) {
        return false;
      }
      return guard.equals(JCiPUtil.findGuardForMember(member));
    }

    private static boolean isSynchronizedOn(@NotNull PsiElement element, @NotNull PsiExpression target) {
      final PsiSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(element, PsiSynchronizedStatement.class);
      if (synchronizedStatement == null) {
        return false;
      }
      final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(synchronizedStatement.getLockExpression());
      final EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
      return checker.expressionsAreEquivalent(lockExpression, target) || isSynchronizedOn(synchronizedStatement, target);
    }

    private static boolean isSynchronizedOnThis(@NotNull PsiElement element) {
      final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiSynchronizedStatement.class);
      if (context instanceof PsiSynchronizedStatement synchronizedStatement) {
        final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(synchronizedStatement.getLockExpression());
        return lockExpression instanceof PsiThisExpression || isSynchronizedOnThis(synchronizedStatement);
      }
      else if (context instanceof PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return true;
        }
      }
      return false;
    }
  }
}
