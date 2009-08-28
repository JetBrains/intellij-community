/*
 * @author max
 */
package com.intellij.lang;

public class CodeInsightActions {
  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    IMPLEMENT_METHOD = new LanguageExtension<LanguageCodeInsightActionHandler>("com.intellij.codeInsight.implementMethod");

  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    OVERRIDE_METHOD = new LanguageExtension<LanguageCodeInsightActionHandler>("com.intellij.codeInsight.overrideMethod");

  public static final LanguageExtension<LanguageCodeInsightActionHandler>
    GOTO_SUPER = new LanguageExtension<LanguageCodeInsightActionHandler>("com.intellij.codeInsight.gotoSuper");

  private CodeInsightActions() {
  }
}