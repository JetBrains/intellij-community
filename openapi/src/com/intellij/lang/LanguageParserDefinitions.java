/*
 * @author max
 */
package com.intellij.lang;

public class LanguageParserDefinitions extends LanguageExtension<ParserDefinition> {
  public static final LanguageParserDefinitions INSTANCE = new LanguageParserDefinitions();

  private LanguageParserDefinitions() {
    super("com.intellij.lang.parserDefinition");
  }
}