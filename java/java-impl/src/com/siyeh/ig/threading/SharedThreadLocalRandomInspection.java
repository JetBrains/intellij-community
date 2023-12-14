// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SharedThreadLocalRandomInspection extends BaseInspection {

  private final MethodMatcher myMethodMatcher;

  public SharedThreadLocalRandomInspection() {
    myMethodMatcher = new MethodMatcher(false, "ignoreArgumentToMethods")
      .add("java.math.BigInteger", ".*")
      .add("java.util.Collections", "shuffle")
      .finishDefault();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(myMethodMatcher.getTable(""));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return myMethodMatcher.getOptionController();
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("shared.thread.local.random.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myMethodMatcher.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SharedThreadLocalRandomVisitor();
  }

  private class SharedThreadLocalRandomVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"current".equals(name)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(aClass, "java.util.concurrent.ThreadLocalRandom")) {
        return;
      }
      if (isArgumentToMethodCall(expression)) {
        registerMethodCallError(expression);
      }
      else {
        final PsiVariable variable = assignedToVariable(expression);
        if (variable instanceof PsiField) {
          registerMethodCallError(expression);
        }
        else if (variable instanceof PsiLocalVariable) {
          final PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
          final boolean passed = VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context, myMethodMatcher::matches);
          if (passed || VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
            registerMethodCallError(expression);
          }
        }
      }
    }

    private boolean isArgumentToMethodCall(PsiExpression expression) {
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      return !myMethodMatcher.matches(methodCallExpression);
    }

    private static PsiVariable assignedToVariable(PsiMethodCallExpression expression) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiVariable) {
        return (PsiVariable)parent;
      }
      if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
        return null;
      }
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
        return null;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        return null;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)target;
    }
  }
}
