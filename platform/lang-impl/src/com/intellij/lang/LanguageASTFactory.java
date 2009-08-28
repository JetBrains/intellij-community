/*
 * @author max
 */
package com.intellij.lang;

public class LanguageASTFactory extends LanguageExtension<ASTFactory> {
  public static final LanguageASTFactory INSTANCE = new LanguageASTFactory();

  private LanguageASTFactory() {
    super("com.intellij.lang.ast.factory", ASTFactory.DEFAULT);
  }
}