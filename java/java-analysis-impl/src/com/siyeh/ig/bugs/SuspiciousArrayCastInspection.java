// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SuspiciousArrayCastInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.array.cast.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousArrayCastVisitor();
  }

  private static class SuspiciousArrayCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) {
        return;
      }
      final PsiType castType = typeElement.getType();
      if (!(castType instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = operand.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      final PsiClass castClass = PsiUtil.resolveClassInClassTypeOnly(castType.getDeepComponentType());
      if (castClass == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type.getDeepComponentType());
      if (aClass == null || !castClass.isInheritor(aClass, true) || isCollectionToArrayCall(operand)) {
        return;
      }
      registerError(typeElement);
    }

    private static boolean isCollectionToArrayCall(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList.getExpressionCount() != 1) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (!"toArray".equals(methodExpression.getReferenceName())) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION);
    }
  }
}
