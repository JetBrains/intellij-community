// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VariableTypeCanBeExplicitInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel10OrHigher(holder.getFile())) { //var won't be parsed as inferred type otherwise
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        List<PsiTypeElement> typeElements = new ArrayList<>();
        for (PsiParameter parameter: expression.getParameterList().getParameters()) {
          PsiTypeElement typeElement = getTypeElementToExpand(parameter);
          if (typeElement == null) return;
          typeElements.add(typeElement);
        }

        for (PsiTypeElement typeElement: typeElements) {
          registerTypeElementProblem(typeElement);
        }
      }

      @Override
      public void visitVariable(PsiVariable variable) {
        PsiTypeElement typeElement = getTypeElementToExpand(variable);
        if (typeElement != null) {
          registerTypeElementProblem(typeElement);
        }
      }

      private void registerTypeElementProblem(PsiTypeElement typeElement) {
        holder.registerProblem(typeElement,
                               "'var' can be replaced with explicit type",
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceVarWithExplicitTypeFix());
      }

      private PsiTypeElement getTypeElementToExpand(PsiVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          PsiType type = variable.getType();
          if (PsiTypesUtil.isDenotableType(type, variable)) {
            return typeElement;
          }
        }
        return null;
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
        PsiElement parent = element.getParent();
        if (parent instanceof PsiParameter) {
          PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
          if (declarationScope instanceof PsiLambdaExpression) {
            for (PsiParameter parameter: ((PsiLambdaExpression)declarationScope).getParameterList().getParameters()) {
              PsiTypeElement typeElement = parameter.getTypeElement();
              if (typeElement != null) {
                PsiTypesUtil.replaceWithExplicitType(typeElement);
              }
            }
          }
        }
        else {
          PsiTypesUtil.replaceWithExplicitType((PsiTypeElement)element);
        }
      }
    }
  }
}
