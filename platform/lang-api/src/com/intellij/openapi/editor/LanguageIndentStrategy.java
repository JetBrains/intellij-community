/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public class LanguageIndentStrategy extends LanguageExtension<IndentStrategy> {
  public static final String EP_NAME = "com.intellij.lang.indentStrategy";
  public static final LanguageIndentStrategy INSTANCE = new LanguageIndentStrategy();

  private static final DefaultIndentStrategy DEFAULT_INDENT_STRATEGY = new DefaultIndentStrategy();

  public LanguageIndentStrategy() {
    super(EP_NAME, DEFAULT_INDENT_STRATEGY);
  }

  @NotNull
  public static IndentStrategy getIndentStrategy(@Nullable PsiFile file) {
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
    public boolean canIndent(@NotNull PsiElement element) {
      return true;
    }
  }
}
