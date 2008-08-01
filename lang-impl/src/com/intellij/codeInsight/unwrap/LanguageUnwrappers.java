package com.intellij.codeInsight.unwrap;

import com.intellij.lang.LanguageExtension;

public class LanguageUnwrappers extends LanguageExtension<UnwrapDescriptor>{
  public static final LanguageUnwrappers INSTANCE = new LanguageUnwrappers();

  public LanguageUnwrappers() {
    super("com.intellij.lang.unwrapDescriptor");
  }
}
