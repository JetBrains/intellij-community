// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
final class JavaModCompletionUtils {
  /**
   * @param type type to represent as {@link MarkupText}
   * @return markup text that represents the type
   */
  static MarkupText typeMarkup(@Nullable PsiType type) {
    return type == null ? MarkupText.empty() :
           MarkupText.plainText(type.getPresentableText());
  }
}
