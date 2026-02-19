// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public final class JavaTypeRenameValidator implements RenameInputValidator {
  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return PsiJavaPatterns.psiClass();
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!element.isValid()) return false;
    Project project = element.getProject();
    LanguageLevel level = PsiUtil.getLanguageLevel(element);
    return PsiNameHelper.getInstance(project).isIdentifier(newName, level) && !PsiTypesUtil.isRestrictedIdentifier(newName, level);
  }
}