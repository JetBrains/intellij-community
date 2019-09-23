// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlainTextSymbolCompletionContributorEP extends LanguageExtensionPoint<PlainTextSymbolCompletionContributor> {
  private static final ExtensionPointName<PlainTextSymbolCompletionContributorEP> EP = new ExtensionPointName<>("com.intellij.completion.plainTextSymbol");
  private static final LanguageExtension<PlainTextSymbolCompletionContributor> INSTANCE = new CompletionExtension<>(EP.getName());

  @Nullable
  public static PlainTextSymbolCompletionContributor forLanguage(@NotNull Language language) {
    return INSTANCE.forLanguage(language);
  }
}
