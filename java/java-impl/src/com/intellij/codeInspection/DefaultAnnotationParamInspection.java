/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultAnnotationParamInspection extends BaseJavaBatchLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNameValuePair(final PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiLiteralExpression)) return;
        PsiReference reference = pair.getReference();
        if (reference == null) return;
        PsiElement element = reference.resolve();
        if (!(element instanceof PsiAnnotationMethod)) return;
        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)element).getDefaultValue();
        if (defaultValue == null) return;
        if (value.getText().equals(defaultValue.getText())) {
          holder.registerProblem(value, "Redundant default parameter value assignment", ProblemHighlightType.LIKE_UNUSED_SYMBOL, new LocalQuickFix() {
            @Nls
            @NotNull
            @Override
            public String getName() {
              return "Remove redundant parameter";
            }

            @Nls
            @NotNull
            @Override
            public String getFamilyName() {
              return getName();
            }

            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
              PsiElement parent = descriptor.getPsiElement().getParent();
              FileModificationService.getInstance().preparePsiElementsForWrite(parent);
              parent.delete();
            }
          });
        }
      }
    };
  }
}
