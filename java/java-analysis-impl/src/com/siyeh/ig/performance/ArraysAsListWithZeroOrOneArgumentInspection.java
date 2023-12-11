// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ArraysAsListWithZeroOrOneArgumentInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean isEmpty = (Boolean)infos[0];
    if (isEmpty.booleanValue()) {
      return InspectionGadgetsBundle.message("arrays.as.list.with.zero.arguments.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("arrays.as.list.with.one.argument.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final boolean isEmpty = (Boolean)infos[0];
    final boolean suggestListOf = (Boolean)infos[1];
    return new ArraysAsListWithOneArgumentFix(isEmpty, suggestListOf);
  }

  private static final class ArraysAsListWithOneArgumentFix extends PsiUpdateModCommandQuickFix {

    private final boolean myEmpty;
    private final boolean mySuggestListOf;

    private ArraysAsListWithOneArgumentFix(boolean isEmpty, boolean suggestListOf) {
      myEmpty = isEmpty;
      mySuggestListOf = suggestListOf;
    }

    @NotNull
    @Override
    public String getName() {
      if (mySuggestListOf) return CommonQuickFixBundle.message("fix.replace.with.x", "List.of()");
      final @NonNls String call = myEmpty ? "Collections.emptyList()" : "Collections.singletonList()";
      return CommonQuickFixBundle.message("fix.replace.with.x", call);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
      final CommentTracker commentTracker = new CommentTracker();
      final String parameterText = parameterList != null ? commentTracker.text(parameterList) : "";
      if (myEmpty) {
        if (mySuggestListOf) {
          PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.List." + parameterText + "of()",
                                                         commentTracker);
        }
        else {
          PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Collections." + parameterText + "emptyList()",
                                                         commentTracker);
        }
      }
      else {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        if (mySuggestListOf) {
          PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression,
                                                         "java.util.List." + parameterText + "of" + commentTracker.text(argumentList),
                                                         commentTracker);
        }
        else {
          PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression,
                                                         "java.util.Collections." + parameterText + "singletonList" + commentTracker.text(argumentList),
                                                         commentTracker);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayAsListWithOneArgumentVisitor();
  }

  private static class ArrayAsListWithOneArgumentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final @NonNls String methodName = methodExpression.getReferenceName();
      if (!"asList".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) return;

      boolean suggestListOf = PsiUtil.isLanguageLevel9OrHigher(expression);
      boolean empty = false;
      if (arguments.length == 0) {
        empty = true;
      }
      else {
        final PsiExpression argument = arguments[0];
        if (!MethodCallUtils.isVarArgCall(expression)) {
          if (!ConstructionUtils.isEmptyArrayInitializer(argument)) {
            return;
          }
          empty = true;
        }
        if (suggestListOf && NullabilityUtil.getExpressionNullability(argument, true) != Nullability.NOT_NULL) {
          // Avoid suggesting List.of with potentially nullable argument
          suggestListOf = false;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.util.Arrays".equals(className)) {
        return;
      }
      registerMethodCallError(expression, empty, suggestListOf);
    }
  }
}
