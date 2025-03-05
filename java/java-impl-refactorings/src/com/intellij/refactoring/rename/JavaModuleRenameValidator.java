// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiNameHelper;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public final class JavaModuleRenameValidator implements RenameInputValidator {
  private final ElementPattern<? extends PsiElement> myPattern = PlatformPatterns.psiElement(PsiJavaModule.class);

  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return PsiNameHelper.isValidModuleName(newName, element);
  }
}