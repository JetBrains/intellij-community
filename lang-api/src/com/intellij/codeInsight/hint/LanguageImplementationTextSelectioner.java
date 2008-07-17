/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.hint;

import com.intellij.lang.LanguageExtension;

public class LanguageImplementationTextSelectioner extends LanguageExtension<ImplementationTextSelectioner>{
  public static final LanguageImplementationTextSelectioner INSTANCE = new LanguageImplementationTextSelectioner();

  public LanguageImplementationTextSelectioner() {
    super("com.intellij.lang.implementationTextSelectioner", new DefaultImplementationTextSelectioner());
  }
}