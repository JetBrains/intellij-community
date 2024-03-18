// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.cloneable;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class UseOfCloneInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Object errorElement = infos[0];
    if (errorElement instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("use.of.clone.call.problem.descriptor");
    }
    else if (errorElement instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("use.of.clone.call.method.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("use.of.clone.reference.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfCloneVisitor();
  }

  private static class UseOfCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (!CloneUtils.isCallToClone(expression)) {
        return;
      }
      final PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiArrayType) {
          return;
        }
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiMethod) || !CloneUtils.isClone((PsiMethod)target) ||
          PsiUtil.isArrayClass(((PsiMethod)target).getContainingClass())) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      final String qualifiedName = reference.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_CLONEABLE.equals(qualifiedName)) {
        return;
      }
      registerError(reference, reference);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!CloneUtils.isClone(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method, method);
    }
  }
}
