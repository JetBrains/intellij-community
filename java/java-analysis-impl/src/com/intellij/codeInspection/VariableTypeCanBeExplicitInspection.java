// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class VariableTypeCanBeExplicitInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel10OrHigher(holder.getFile())) { //var won't be parsed as inferred type otherwise
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitVariable(PsiVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          PsiType type = variable.getType();
          if (PsiTypesUtil.isDenotableType(type, variable)) {
            holder.registerProblem(typeElement,
                                   "'var' can be replaced with explicit type",
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   new ReplaceVarWithExplicitTypeFix());
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
        PsiTypesUtil.replaceWithExplicitType((PsiTypeElement)element);
      }
    }
  }
}
