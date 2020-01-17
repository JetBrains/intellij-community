/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class JavaTypeRenameValidator implements RenameInputValidator {
  private final ElementPattern<? extends PsiElement> myPattern = PlatformPatterns.psiElement(PsiClass.class);

  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    PsiFile file = element.getContainingFile();
    LanguageLevel level = PsiUtil.getLanguageLevel(file);
    return PsiNameHelper.getInstance(file.getProject()).isIdentifier(newName, level) &&
           !HighlightClassUtil.isRestrictedIdentifier(newName, level);
  }
}