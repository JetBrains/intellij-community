// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceVarWithExplicitTypeFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class VariableTypeCanBeExplicitInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LVTI);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
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

      private void registerTypeElementProblem(@NotNull PsiTypeElement typeElement) {
        holder.problem(typeElement, JavaAnalysisBundle.message("var.can.be.replaced.with.explicit.type"))
          .fix(new ReplaceVarWithExplicitTypeFix(typeElement))
          .register();
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
