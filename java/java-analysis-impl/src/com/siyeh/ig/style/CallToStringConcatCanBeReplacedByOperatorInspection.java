// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CallToStringConcatCanBeReplacedByOperatorInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "call.to.string.concat.can.be.replaced.by.operator.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new CallToStringConcatCanBeReplacedByOperatorFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToStringConcatCanBeReplacedByOperatorVisitor();
  }

  private static class CallToStringConcatCanBeReplacedByOperatorFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("call.to.string.concat.can.be.replaced.by.operator.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement;
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement grandParent = referenceExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      CommentTracker tracker = new CommentTracker();
      @NonNls final String newExpression = tracker.text(qualifier) + '+' + tracker.text(argument);
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression, tracker);
    }
  }

  private static class CallToStringConcatCanBeReplacedByOperatorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final Project project = expression.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass stringClass =
        psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING,
                            expression.getResolveScope());
      if (stringClass == null) {
        return;
      }
      final PsiClassType stringType =
        psiFacade.getElementFactory().createType(stringClass);
      if (!MethodCallUtils.isCallToMethod(expression,
                                          CommonClassNames.JAVA_LANG_STRING,
                                          stringType, "concat", stringType)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      if (ExpressionUtils.isVoidContext(expression)) return;
      registerMethodCallError(expression);
    }
  }
}