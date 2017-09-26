/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class VariableTypeCanBeExplicitInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_X)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(holder.getProject());
    return new JavaElementVisitor() {
      @Override
      public void visitVariable(PsiVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          PsiType type = variable.getType();
          try {
            PsiType typeAfterReplacement = elementFactory.createTypeElementFromText(type.getCanonicalText(), variable).getType();
            if (type.equals(typeAfterReplacement)) {
              holder.registerProblem(typeElement,
                                     "'var' can be replaced with explicit type",
                                     ProblemHighlightType.INFORMATION,
                                     new ReplaceVarWithExplicitTypeFix());
            }
          }
          catch (IncorrectOperationException e) {
            //non-denotable type
          }
        }
      }
    };
  }

  private static class ReplaceVarWithExplicitTypeFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace 'var' with explicit type";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiTypeElement) {
        PsiTypeElement typeElementByExplicitType = JavaPsiFacade.getElementFactory(project)
          .createTypeElement(((PsiTypeElement)element).getType());
        PsiElement explicitTypeElement = element.replace(typeElementByExplicitType);
        explicitTypeElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(explicitTypeElement);
        CodeStyleManager.getInstance(project).reformat(explicitTypeElement);
      }
    }
  }
}
