/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;

import java.util.List;

public class LanguageDocumentation extends LanguageExtension<DocumentationProvider> {
  public static final LanguageDocumentation INSTANCE = new LanguageDocumentation();

  private LanguageDocumentation() {
    super("com.intellij.lang.documentationProvider");
  }

  public DocumentationProvider forLanguage(final Language l) {
    final List<DocumentationProvider> providers = allForLanguage(l);
    if (providers.size() < 2) {
      return super.forLanguage(l);
    }

    return new CompositeDocumentationProvider(providers);
  }
}