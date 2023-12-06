// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageIndentStrategy extends LanguageExtension<IndentStrategy> {
  public static final String EP_NAME = "com.intellij.lang.indentStrategy";
  public static final LanguageIndentStrategy INSTANCE = new LanguageIndentStrategy();

  private static final DefaultIndentStrategy DEFAULT_INDENT_STRATEGY = new DefaultIndentStrategy();

  public LanguageIndentStrategy() {
    super(EP_NAME, DEFAULT_INDENT_STRATEGY);
  }

  public static @NotNull IndentStrategy getIndentStrategy(@Nullable PsiFile file) {
    if (file != null) {
      Language language = file.getLanguage();
      IndentStrategy strategy = INSTANCE.forLanguage(language);
      if (strategy != null) {
        return strategy;
      }
    }
    return DEFAULT_INDENT_STRATEGY;
  }

  public static boolean isDefault(IndentStrategy indentStrategy) {
    return indentStrategy == DEFAULT_INDENT_STRATEGY;
  }

  private static class DefaultIndentStrategy implements IndentStrategy {
    @Override
    public boolean canIndent(int indentationStartOffset, int indentationEndOffset, @NotNull PsiElement element) {
      return true;
    }
  }
}
