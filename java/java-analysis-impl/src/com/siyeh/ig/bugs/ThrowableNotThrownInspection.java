// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ThrowableNotThrownInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (expression instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("throwable.result.of.method.call.ignored.problem.descriptor");
    }
    final String type =
      TypeUtils.expressionHasTypeOrSubtype(expression,
                                           CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_ERROR);
    if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.runtime.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.checked.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_ERROR.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.error.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return ThrowableNotThrownFix.createFix((PsiCallExpression)infos[0]);
  }

  private static class ThrowableNotThrownFix extends PsiUpdateModCommandQuickFix {
    private ThrowableNotThrownFix() {}
    private static ThrowableNotThrownFix createFix(PsiCallExpression context) {
      final PsiElement parent = context.getParent();
      if (!(parent instanceof PsiExpressionStatement)) {
        return null;
      }
      final PsiElement next = PsiTreeUtil.getNextSiblingOfType(parent, PsiStatement.class);
       return next == null ? new ThrowableNotThrownFix() : null;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.insert.x", "throw ");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent();
      if (!(element instanceof PsiCallExpression)) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionStatement)) {
        return;
      }
      PsiReplacementUtil.replaceStatement((PsiStatement)parent, "throw " + element.getText() + ';');
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowableResultOfMethodCallIgnoredVisitor();
  }

  private static class ThrowableResultOfMethodCallIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isIgnoredThrowable(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isIgnoredThrowable(expression)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final StandardMethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodContracts(method));
      if (contract != null && contract.isTrivial() && contract.getReturnValue().isFail()) {
        return;
      }
      if (MethodUtils.hasCanIgnoreReturnValueAnnotation(method, method.getContainingFile() /* check only in current file */)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }

  private static boolean isIgnoredThrowable(PsiExpression expression) {
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return false;
    }
    return isIgnored(expression, true);
  }

  private static boolean isIgnored(PsiExpression expression, boolean checkDeep) {
    final PsiElement parent = getHandlingParent(expression);
    if (parent instanceof PsiVariable) {
      if (!(parent instanceof PsiLocalVariable)) {
        return false;
      }
      else {
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)parent);
      }
    }
    else if (parent instanceof PsiExpressionStatement expressionStatement) {
      // void method (like printStackTrace()) provides no result, thus can't be ignored
      final PsiExpression expression1 = expressionStatement.getExpression();
      return !PsiTypes.voidType().equals(expression1.getType());
    }
    else if (parent instanceof PsiExpressionList) {
      return parent.getParent() instanceof PsiExpressionListStatement;
    }
    else if (parent instanceof PsiLambdaExpression) {
      return PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent));
    }
    else if (parent instanceof PsiReturnStatement || parent instanceof PsiThrowStatement || parent instanceof PsiLoopStatement
             || parent instanceof PsiIfStatement || parent instanceof PsiAssertStatement) {
      return false;
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
        return false;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiLocalVariable)) {
        return false;
      }
      return checkDeep && !isUsedElsewhere((PsiLocalVariable)target);
    }
    return true;
  }

  private static PsiElement getHandlingParent(PsiExpression expression) {
    while (true) {
      final PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
      if (!(parent instanceof PsiExpression) || parent instanceof PsiLambdaExpression || parent instanceof PsiAssignmentExpression) {
        return parent;
      }
      expression = (PsiExpression)parent;
    }
  }

  private static boolean isUsedElsewhere(PsiLocalVariable variable) {
    final Query<PsiReference> query = ReferencesSearch.search(variable);
    for (PsiReference reference : query) {
      if (reference instanceof PsiReferenceExpression && !isIgnored((PsiExpression)reference, false)) {
        return true;
      }
    }
    return false;
  }
}