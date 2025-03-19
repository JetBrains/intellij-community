// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;


import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class PostfixTemplatePsiInfo {
  public abstract @NotNull PsiElement createExpression(@NotNull PsiElement context,
                                                       @NotNull String prefix,
                                                       @NotNull String suffix);

  /**
   * You can assume that {@code element} is an element produced by {@code createExpression}
   */
  public abstract @NotNull PsiElement getNegatedExpression(@NotNull PsiElement element);
}
