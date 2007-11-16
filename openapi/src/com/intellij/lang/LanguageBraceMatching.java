package com.intellij.lang;

/**
 * @author yole
 */
public class LanguageBraceMatching extends LanguageExtension<PairedBraceMatcher> {
  public static final LanguageBraceMatching INSTANCE = new LanguageBraceMatching();

  private LanguageBraceMatching() {
    super("com.intellij.lang.braceMatcher");
  }
}