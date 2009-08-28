package com.intellij.testIntegration;

import com.intellij.lang.LanguageExtension;

public class LanguageTestCreators extends LanguageExtension<TestCreator> {
  public static final LanguageTestCreators INSTANCE = new LanguageTestCreators();

  public LanguageTestCreators() {
    super("com.intellij.testCreator");
  }
}
