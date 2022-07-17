// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LanguageDocumentation extends LanguageExtension<DocumentationProvider> {
  public static final LanguageDocumentation INSTANCE = new LanguageDocumentation();

  private LanguageDocumentation() {
    super("com.intellij.lang.documentationProvider");
  }

  /**
   * This method is left to preserve binary compatibility.
   */
  @SuppressWarnings("RedundantMethodOverride")
  @Override
  public DocumentationProvider forLanguage(@NotNull final Language l) {
    return super.forLanguage(l);
  }

  @Override
  protected DocumentationProvider findForLanguage(@NotNull Language language) {
    List<DocumentationProvider> providers = allForLanguage(language);
    if (providers.isEmpty()) {
      return null;
    }
    return CompositeDocumentationProvider.wrapProviders(providers);
  }
}
