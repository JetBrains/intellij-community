/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class JavaTypeRenameValidator implements RenameInputValidator {
  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PsiJavaPatterns.psiClass();
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    Project project = element.getProject();
    LanguageLevel level = PsiUtil.getLanguageLevel(element);
    return PsiNameHelper.getInstance(project).isIdentifier(newName, level) &&
           !HighlightClassUtil.isRestrictedIdentifier(newName, level);
  }
}