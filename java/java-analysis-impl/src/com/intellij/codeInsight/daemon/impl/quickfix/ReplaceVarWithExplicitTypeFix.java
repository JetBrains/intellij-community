// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.VariableTypeCanBeExplicitInspection;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceVarWithExplicitTypeFix extends PsiUpdateModCommandAction<PsiTypeElement> {
  public ReplaceVarWithExplicitTypeFix(@NotNull PsiTypeElement element) {
    super(element);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("replace.var.with.explicit.type");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement startElement, @NotNull ModPsiUpdater updater) {
    if (startElement.getParent() instanceof PsiParameter psiParameter &&
        psiParameter.getDeclarationScope() instanceof PsiLambdaExpression lambda) {
      for (PsiParameter parameter : lambda.getParameterList().getParameters()) {
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          PsiTypesUtil.replaceWithExplicitType(typeElement);
        }
      }
      return;
    }
    PsiTypesUtil.replaceWithExplicitType(startElement);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement startElement) {
    return isAvailable(startElement) ? Presentation.of(getFamilyName()) : null;
  }

  private static boolean isAvailable(@NotNull PsiTypeElement startElement) {
    PsiElement parent = startElement.getParent();
    if (parent instanceof PsiParameter psiParameter) {
      PsiElement declarationScope = psiParameter.getDeclarationScope();
      if (declarationScope instanceof PsiLambdaExpression lambda) {
        return ContainerUtil.and(lambda.getParameterList().getParameters(),
                                 parameter -> VariableTypeCanBeExplicitInspection.getTypeElementToExpand(parameter) != null);
      }
    }
    if (parent instanceof PsiVariable variable) {
      return VariableTypeCanBeExplicitInspection.getTypeElementToExpand(variable) != null;
    }
    return false;
  }
}