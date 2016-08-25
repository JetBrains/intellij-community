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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class InvalidComparatorMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        PsiElement referenceNameElement = expression.getReferenceNameElement();
        if (!(qualifierExpression instanceof PsiReferenceExpressionBase) || referenceNameElement == null) {
          return;
        }
        String name = referenceNameElement.getText();
        if (!name.equals("min") && !name.equals("max")) {
          return;
        }
        PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
        if (!(functionalInterfaceType instanceof PsiClassType)) {
          return;
        }
        PsiClass targetType = ((PsiClassType)functionalInterfaceType).resolve();
        if (targetType == null) {
          return;
        }
        String targetClassName = targetType.getQualifiedName();
        if (targetClassName == null || !targetClassName.equals(CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          return;
        }
        PsiElement refType = ((PsiReference)qualifierExpression).resolve();
        if (!(refType instanceof PsiClass)) {
          return;
        }
        String className = ((PsiClass)refType).getQualifiedName();
        if (!CommonClassNames.JAVA_LANG_INTEGER.equals(className) && !CommonClassNames.JAVA_LANG_MATH
          .equals(className)) {
          return;
        }
        //noinspection DialogTitleCapitalization
        holder
          .registerProblem(expression,
                           "Method reference mapped to Comparator interface does not fulfill the Comparator contract",
                           new ReplaceWithComparatorQuickFix(name.equals("min")));
      }
    };
  }

  private static class ReplaceWithComparatorQuickFix implements LocalQuickFix {
    private final boolean reverse;

    public ReplaceWithComparatorQuickFix(boolean reverse) {
      this.reverse = reverse;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with " + (reverse ? "Comparator.reverseOrder()" : "Comparator.naturalOrder()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with comparator";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      PsiElement parent = element.getParent();
      if (parent != null) {
        PsiExpression newMethodExpression = JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText(CommonClassNames.JAVA_UTIL_COMPARATOR + "." + (reverse ? "reverseOrder()" : "naturalOrder()"), parent);
        PsiElement shortMethodExpression = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newMethodExpression);
        element.replace(shortMethodExpression);
      }
    }
  }
}
