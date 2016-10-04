/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class Java8ListSortInspection extends BaseJavaBatchLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiElement nameElement = expression.getMethodExpression().getReferenceNameElement();
        if(nameElement != null && expression.getArgumentList().getExpressions().length == 2 &&
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
            ParenthesesUtils.getText(list, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".sort(" + comparator.getText() + ")";
          if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
          methodCallExpression
            .replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, methodCallExpression));
        }
      }
    }
  }
}