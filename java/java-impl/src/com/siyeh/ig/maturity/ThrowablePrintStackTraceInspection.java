// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ThrowablePrintStackTraceInspection extends BaseInspection {

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  public @NotNull String getID() {
    return "CallToPrintStackTrace";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("printstacktrace.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowablePrintStackTraceVisitor();
  }

  private static class ThrowablePrintStackTraceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final String methodName = MethodCallUtils.getMethodName(expression);
      if (!HardcodedMethodConstants.PRINT_STACK_TRACE.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (!argumentList.isEmpty()) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(containingClass, false, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
