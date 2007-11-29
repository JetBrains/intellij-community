/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;

public class ExternalLanguageAnnotators extends LanguageExtension<ExternalAnnotator>{
  public static final ExternalLanguageAnnotators INSTANCE = new ExternalLanguageAnnotators();

  private ExternalLanguageAnnotators() {
    super("com.intellij.externalAnnotator");
  }
}