// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class CompletionConfidenceEP extends LanguageExtensionPoint<CompletionConfidence> {
  private static final LanguageExtension<CompletionConfidence> INSTANCE = new CompletionExtension<>("com.intellij.completion.confidence");

  public static List<CompletionConfidence> forLanguage(@NotNull Language language) {
    return INSTANCE.allForLanguage(language);
  }
}
