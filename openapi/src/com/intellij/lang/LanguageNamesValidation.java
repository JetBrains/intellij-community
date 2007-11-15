/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.refactoring.JavaNamesValidator;
import com.intellij.lang.refactoring.NamesValidator;

public class LanguageNamesValidation extends LanguageExtension<NamesValidator> {
  public static final LanguageNamesValidation INSTANCE = new LanguageNamesValidation();

  private LanguageNamesValidation() {
    super("com.intellij.lang.namesValidator", new JavaNamesValidator());
  }
}