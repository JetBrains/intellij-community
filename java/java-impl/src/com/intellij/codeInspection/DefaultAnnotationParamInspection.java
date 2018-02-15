// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultAnnotationParamInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNameValuePair(final PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        PsiReference reference = pair.getReference();
        if (reference == null) return;
        PsiElement element = reference.resolve();
        if (!(element instanceof PsiAnnotationMethod)) return;
        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)element).getDefaultValue();
        if (defaultValue == null) return;
        if (AnnotationUtil.equal(value, defaultValue)) {
          holder.registerProblem(value, "Redundant default parameter value assignment", ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 createRemoveParameterFix());
        }
      }
    };
  }

  @NotNull
  private static LocalQuickFix createRemoveParameterFix() {
    return new LocalQuickFix() {
      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return "Remove redundant parameter";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement parent = descriptor.getPsiElement().getParent();
        parent.delete();
      }
    };
  }
}
