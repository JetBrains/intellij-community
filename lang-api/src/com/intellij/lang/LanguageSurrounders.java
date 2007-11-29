/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.surroundWith.SurroundDescriptor;

public class LanguageSurrounders extends LanguageExtension<SurroundDescriptor> {
  public static final LanguageSurrounders INSTANCE = new LanguageSurrounders();

  private LanguageSurrounders() {
    super("com.intellij.lang.surroundDescriptor");
  }
}