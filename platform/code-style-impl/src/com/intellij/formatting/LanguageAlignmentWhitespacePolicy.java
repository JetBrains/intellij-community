// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LanguageAlignmentWhitespacePolicy extends LanguageExtension<AlignmentWhitespacePolicy> {
  private static final LanguageAlignmentWhitespacePolicy INSTANCE = new LanguageAlignmentWhitespacePolicy();

  private LanguageAlignmentWhitespacePolicy() {
    super("com.intellij.lang.formatter.alignmentWhitespacePolicy");
  }

  static boolean useSpacesForAlignment(@Nullable AbstractBlockWrapper block) {
    if (block == null) return false;
    Language language = block.getLanguage();
    if (language == null) return false;
    AlignmentWhitespacePolicy policy = INSTANCE.forLanguage(language);
    return policy != null && policy.useSpacesForAlignment();
  }
}
