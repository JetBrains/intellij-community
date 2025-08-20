// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.LanguageExtensionWithAny;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class CompletionConfidenceEP extends LanguageExtensionPoint<CompletionConfidence> {
  private static final LanguageExtension<CompletionConfidence> INSTANCE = new LanguageExtensionWithAny<>("com.intellij.completion.confidence");

  public static List<CompletionConfidence> forLanguage(@NotNull Language language) {
    return INSTANCE.allForLanguage(language);
  }
}
