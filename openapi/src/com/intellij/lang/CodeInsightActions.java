/*
 * @author max
 */
package com.intellij.lang;

public class CodeInsightActions {
  public static LanguageExtension<LanguageCodeInsightActionHandler>
    IMPLEMENT_METHOD = new LanguageExtension<LanguageCodeInsightActionHandler>("com.intellij.codeInsight.implementMethod");

  public static LanguageExtension<LanguageCodeInsightActionHandler>
    OVERRIDE_METHOD = new LanguageExtension<LanguageCodeInsightActionHandler>("com.intellij.codeInsight.overrideMethod");

  private CodeInsightActions() {
  }
}