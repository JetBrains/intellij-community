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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class TrivialMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        if (qualifierExpression != null && referenceNameElement != null) {
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(expression);
          if (interfaceMethod != null) {
            final PsiElement resolve = expression.resolve();
            if (resolve instanceof PsiMethod && 
                (interfaceMethod == resolve || MethodSignatureUtil.isSuperMethod(interfaceMethod, (PsiMethod)resolve))) {
              holder.registerProblem(referenceNameElement, "Method reference can be replaced with qualifier", new ReplaceMethodRefWithQualifierFix());
            }
          }
        }
      }
    };
  }

  private static class ReplaceMethodRefWithQualifierFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with qualifier";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      final PsiElement parent = element != null ? element.getParent() : null;
      if (parent instanceof PsiMethodReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)parent).getQualifierExpression();
        if (qualifierExpression != null) {
          parent.replace(qualifierExpression);
        }
      }
    }
  }
}
