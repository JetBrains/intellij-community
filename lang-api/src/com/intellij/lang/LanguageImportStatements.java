/*
 * @author max
 */
package com.intellij.lang;

public class LanguageImportStatements extends LanguageExtension<ImportOptimizer> {
  public static final LanguageImportStatements INSTANCE = new LanguageImportStatements();

  private LanguageImportStatements() {
    super("com.intellij.lang.importOptimizer");
  }
}