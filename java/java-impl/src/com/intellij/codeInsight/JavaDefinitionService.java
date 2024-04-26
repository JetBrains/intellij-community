// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.definition.AbstractBasicJavaDefinitionService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaDefinitionService extends AbstractBasicJavaDefinitionService {

  @Override
  public @NotNull LanguageLevel getLanguageLevel(@NotNull PsiElement psiElement) {
    return PsiUtil.getLanguageLevel(psiElement);
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel(@NotNull Project project) {
    return PsiUtil.getLanguageLevel(project);
  }
}
