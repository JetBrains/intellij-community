package com.intellij.lang.findUsages;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LanguageFindUsages extends LanguageExtension<FindUsagesProvider> {
  public static final LanguageFindUsages INSTANCE = new LanguageFindUsages();

  private LanguageFindUsages() {
    super("com.intellij.lang.findUsagesProvider", new EmptyFindUsagesProvider());
  }
}