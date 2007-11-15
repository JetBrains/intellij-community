/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.documentation.DocumentationProvider;

public class LanguageDocumentation extends LanguageExtension<DocumentationProvider> {
  public static final LanguageDocumentation INSTANCE = new LanguageDocumentation();

  private LanguageDocumentation() {
    super("com.intellij.lang.documentationProvider");
  }
}