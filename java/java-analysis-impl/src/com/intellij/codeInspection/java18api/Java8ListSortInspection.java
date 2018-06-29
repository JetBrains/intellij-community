// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class Java8ListSortInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiElement nameElement = expression.getMethodExpression().getReferenceNameElement();
        if(nameElement != null && expression.getArgumentList().getExpressionCount() == 2 &&
          "sort".equals(nameElement.getText())) {
          PsiMethod method = expression.resolveMethod();
          if(method != null) {
            PsiClass containingClass = method.getContainingClass();
            if(containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
              //noinspection DialogTitleCapitalization
              holder.registerProblem(nameElement, QuickFixBundle.message("java.8.list.sort.inspection.description"),
                                     new ReplaceWithListSortFix());
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithListSortFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.list.sort.inspection.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
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