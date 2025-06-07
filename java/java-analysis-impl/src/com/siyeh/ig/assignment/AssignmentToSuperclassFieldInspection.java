// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.assignment;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentToSuperclassFieldInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[0];
    final PsiClass superclass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("assignment.to.superclass.field.problem.descriptor",
                                           referenceExpression.getReferenceName(), superclass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToSuperclassFieldVisitor();
  }

  private static class AssignmentToSuperclassFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      checkSuperclassField(lhs);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final PsiExpression operand = expression.getOperand();
      checkSuperclassField(operand);
    }

    private void checkSuperclassField(PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      if (qualifierExpression != null &&
          !(qualifierExpression instanceof PsiThisExpression) && !(qualifierExpression instanceof PsiSuperExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField field)) {
        return;
      }
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass == null) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (method == null || !method.isConstructor()) {
        return;
      }
      final PsiClass assignmentClass = method.getContainingClass();
      final String name = fieldClass.getQualifiedName();
      if (name == null || !InheritanceUtil.isInheritor(assignmentClass, true, name)) {
        return;
      }
      registerError(expression, referenceExpression, fieldClass);
    }
  }
}
