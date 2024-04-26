// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class LawOfDemeterInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreLibraryCalls = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    boolean isMethodCall = (Boolean)infos[0];
    return isMethodCall
           ? InspectionGadgetsBundle.message("law.of.demeter.problem.descriptor")
           : InspectionGadgetsBundle.message("law.of.demeter.field.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreLibraryCalls", InspectionGadgetsBundle.message("law.of.demeter.ignore.library.calls.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LawOfDemeterVisitor();
  }

  private class LawOfDemeterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!isCallToSuspiciousMethod(expression)) {
        return;
      }
      if (violatesLawOfDemeter(qualifier)) {
        registerMethodCallError(expression, Boolean.TRUE);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getParent() instanceof PsiMethodCallExpression) {
        return;
      }
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement nameElement = expression.getReferenceNameElement();
      if (nameElement == null) {
        return;
      }
      if (violatesLawOfDemeter(qualifier)) {
        registerError(nameElement, Boolean.FALSE);
      }
    }

    private boolean isCallToSuspiciousMethod(PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null || MethodUtils.isFactoryMethod(method)) {
        return false;
      }
      if (ignoreLibraryCalls && method instanceof PsiCompiledElement) {
        return false;
      }
      if (method.getContainingFile() == expression.getContainingFile()) {
        // consider code in the same file a "friend"
        return false;
      }
      final PsiType type = method.getReturnType();
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (method.getContainingClass() == aClass) {
        return false;
      }
      return true;
    }

    public boolean violatesLawOfDemeter(PsiExpression expression) {
      final PsiElement qualifier = PsiUtil.skipParenthesizedExprUp(expression);
      if (qualifier instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiParameter) {
          return false;
        }
        else if (target instanceof PsiField field) {
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
          }
          else if (field.getContainingFile() == expression.getContainingFile()) {
            return false;
          }
          else if (ignoreLibraryCalls && field instanceof PsiCompiledElement) {
            return false;
          }
          return true;
        }
        else if (target instanceof PsiLocalVariable variable) {
          if (ignoreLibraryCalls && variable.getType() instanceof PsiArrayType) {
            return false;
          }
          final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
          return violatesLawOfDemeter(definition);
        }
      }
      else if (qualifier instanceof PsiMethodCallExpression methodCallExpression) {
        if (isCallToSuspiciousMethod(methodCallExpression)) {
          return true;
        }
      }
      return false;
    }
  }
}