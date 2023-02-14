// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceVarWithExplicitTypeFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VariableTypeCanBeExplicitInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel10OrHigher(holder.getFile())) { //var won't be parsed as inferred type otherwise
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
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
      public void visitVariable(@NotNull PsiVariable variable) {
        if (variable instanceof PsiParameter && 
            ((PsiParameter)variable).getDeclarationScope() instanceof PsiLambdaExpression) {
          return;
        }
        PsiTypeElement typeElement = getTypeElementToExpand(variable);
        if (typeElement != null) {
          registerTypeElementProblem(typeElement);
        }
      }

      private void registerTypeElementProblem(PsiTypeElement typeElement) {
        holder.registerProblem(typeElement,
                               JavaAnalysisBundle.message("var.can.be.replaced.with.explicit.type"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceVarWithExplicitTypeFix(typeElement));
      }
    };
  }

  public static PsiTypeElement getTypeElementToExpand(PsiVariable variable) {
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement != null && typeElement.isInferredType()) {
      PsiType type = variable.getType();
      if (PsiTypesUtil.isDenotableType(type, variable)) {
        return typeElement;
      }
    }
    return null;
  }
}
