// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class LanguageWordCompletion extends LanguageExtension<WordCompletionElementFilter> {
  public static final LanguageWordCompletion INSTANCE = new LanguageWordCompletion();

  private LanguageWordCompletion() {
    super("com.intellij.codeInsight.wordCompletionFilter", new DefaultWordCompletionFilter());
  }

  public boolean isEnabledIn(@NotNull IElementType type) {
    return forLanguage(type.getLanguage()).isWordCompletionEnabledIn(type);
  }
}