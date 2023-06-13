// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.VariableTypeCanBeExplicitInspection;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceVarWithExplicitTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public ReplaceVarWithExplicitTypeFix(@Nullable PsiTypeElement element) {
    super(element);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("replace.var.with.explicit.type");
  }


  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (startElement instanceof PsiTypeElement) {
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
      PsiTypesUtil.replaceWithExplicitType((PsiTypeElement)startElement);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (startElement instanceof PsiTypeElement) {
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
    }
    return false;
  }
}