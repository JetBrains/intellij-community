// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public class TopLevelCompletionContributorEP extends LanguageExtensionPoint<TopLevelCompletionContributor> {
  private static final ExtensionPointName<TopLevelCompletionContributorEP> EP = new ExtensionPointName<>("com.intellij.completion.toplevel");
  private static final LanguageExtension<TopLevelCompletionContributor> INSTANCE = new CompletionExtension<>(EP.getName());

  public static TopLevelCompletionContributor forLanguage(@NotNull Language language) {
    return INSTANCE.forLanguage(language);
  }
}
