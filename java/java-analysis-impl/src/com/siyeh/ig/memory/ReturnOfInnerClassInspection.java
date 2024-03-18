// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.memory;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * @author Bas Leijdekkers
 */
public final class ReturnOfInnerClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreNonPublic = false;

  private enum ClassType { ANONYMOUS_CLASS, LOCAL_CLASS, INNER_CLASS }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return switch ((ClassType)infos[0]) {
      case ANONYMOUS_CLASS -> InspectionGadgetsBundle.message("return.of.anonymous.class.problem.descriptor");
      case LOCAL_CLASS -> InspectionGadgetsBundle.message("return.of.local.class.problem.descriptor", ((PsiClass)infos[1]).getName());
      case INNER_CLASS -> InspectionGadgetsBundle.message("return.of.inner.class.problem.descriptor", ((PsiClass)infos[1]).getName());
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonPublic", InspectionGadgetsBundle.message("return.of.inner.class.ignore.non.public.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfInnerClassVisitor();
  }

  private  class ReturnOfInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(statement.getReturnValue());
      if (expression == null) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      else if (ignoreNonPublic &&
               (method.hasModifierProperty(PsiModifier.PROTECTED) || method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))) {
        return;
      }
      if (expression instanceof PsiNewExpression newExpression) {
        final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null) {
          registerStatementError(statement, ClassType.ANONYMOUS_CLASS);
          return;
        }
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (aClass == null) {
        return;
      }
      if (PsiUtil.isLocalClass(aClass)) {
        registerStatementError(statement, ClassType.LOCAL_CLASS, aClass);
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null || aClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerStatementError(statement, ClassType.INNER_CLASS, aClass);
    }
  }
}
