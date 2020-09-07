// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      PsiElement parent = startElement.getParent();
      if (parent instanceof PsiParameter) {
        PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
        if (declarationScope instanceof PsiLambdaExpression) {
          for (PsiParameter parameter : ((PsiLambdaExpression)declarationScope).getParameterList().getParameters()) {
            PsiTypeElement typeElement = parameter.getTypeElement();
            if (typeElement != null) {
              PsiTypesUtil.replaceWithExplicitType(typeElement);
            }
          }
          return;
        }
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
      if (parent instanceof PsiParameter ) {
        PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
        if (declarationScope instanceof PsiLambdaExpression) {
          return ContainerUtil.and(((PsiLambdaExpression)declarationScope).getParameterList().getParameters(), 
                                   parameter -> VariableTypeCanBeExplicitInspection.getTypeElementToExpand(parameter) != null);
        }
      }
      if (parent instanceof PsiVariable) {
        return VariableTypeCanBeExplicitInspection.getTypeElementToExpand((PsiVariable)parent) != null;
      }
    }
    return false;
  }
}