/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.LanguageExtension;

public class SyntaxHighlighterLanguageFactory extends LanguageExtension<SyntaxHighlighterFactory> {
  SyntaxHighlighterLanguageFactory() {
    super("com.intellij.lang.syntaxHighlighterFactory", new PlainSyntaxHighlighterFactory());
  }
}