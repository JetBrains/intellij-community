/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.Annotator;

public class LanguageAnnotators extends LanguageExtension<Annotator> {
  public static final LanguageAnnotators INSTANCE = new LanguageAnnotators();

  private LanguageAnnotators() {
    super("com.intellij.annotator");
  }
}