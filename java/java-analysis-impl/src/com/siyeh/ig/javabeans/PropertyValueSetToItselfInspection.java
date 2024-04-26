// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javabeans;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class PropertyValueSetToItselfInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("property.value.set.to.itself.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PropertyValueSetToItselfVisitor();
  }

  private static class PropertyValueSetToItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList1 = expression.getArgumentList();
      final PsiExpression[] arguments1 = argumentList1.getExpressions();
      if (arguments1.length != 1) {
        return;
      }
      final PsiExpression argument = arguments1[0];
      if (!(argument instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      if (!methodCallExpression.getArgumentList().isEmpty()) {
        return;
      }
      final PsiReferenceExpression methodExpression1 = expression.getMethodExpression();
      final PsiExpression qualifierExpression1 = PsiUtil.skipParenthesizedExprDown(methodExpression1.getQualifierExpression());
      final PsiReferenceExpression methodExpression2 = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression2 = PsiUtil.skipParenthesizedExprDown(methodExpression2.getQualifierExpression());
      if (qualifierExpression1 instanceof PsiReferenceExpression && qualifierExpression2 instanceof PsiReferenceExpression) {
        if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifierExpression1, qualifierExpression2)) {
          return;
        }
      }
      else if((qualifierExpression1 != null &&
              !(qualifierExpression1 instanceof PsiThisExpression) &&
              !(qualifierExpression1 instanceof PsiSuperExpression))
              ||
              qualifierExpression2 != null &&
              !(qualifierExpression2 instanceof PsiThisExpression) &&
              !(qualifierExpression2 instanceof PsiSuperExpression)) {
        return;
      }
      final PsiMethod method1 = expression.resolveMethod();
      final PsiField fieldOfSetter = PropertyUtil.getFieldOfSetter(method1);
      if (fieldOfSetter == null) {
        return;
      }
      final PsiMethod method2 = methodCallExpression.resolveMethod();
      final PsiField fieldOfGetter = PropertyUtil.getFieldOfGetter(method2);
      if (!fieldOfSetter.equals(fieldOfGetter)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
