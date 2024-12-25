// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class Java8ListSortInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ADVANCED_COLLECTIONS_API);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiElement nameElement = expression.getMethodExpression().getReferenceNameElement();
        if(nameElement != null && expression.getArgumentList().getExpressionCount() == 2 &&
          "sort".equals(nameElement.getText())) {
          PsiMethod method = expression.resolveMethod();
          if(method != null) {
            PsiClass containingClass = method.getContainingClass();
            if(containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
              holder.registerProblem(nameElement, QuickFixBundle.message("java.8.list.sort.inspection.description"),
                                     new ReplaceWithListSortFix());
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithListSortFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return QuickFixBundle.message("java.8.list.sort.inspection.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if(methodCallExpression != null) {
        PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
        if(args.length == 2) {
          PsiExpression list = args[0];
          PsiExpression comparator = args[1];
          String replacement =
            ParenthesesUtils.getText(list, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".sort(" + comparator.getText() + ")";
          methodCallExpression
            .replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, methodCallExpression));
        }
      }
    }
  }
}