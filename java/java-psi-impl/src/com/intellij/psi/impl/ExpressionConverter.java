// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public abstract class ExpressionConverter {
  public static final LanguageExtension<ExpressionConverter> EP =
    new LanguageExtension<>("com.intellij.expressionConverter");

  protected abstract PsiElement convert(PsiElement expression, Project project);

  public static @Nullable PsiElement getExpression(PsiElement expression, Language language, Project project) {
    if (expression.getLanguage() == language) return expression;

    final ExpressionConverter converter = EP.forLanguage(language);
    if (converter == null) return null;
    return converter.convert(expression, project);
  }
}
