package com.intellij.lang.folding;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LanguageFolding extends LanguageExtension<FoldingBuilder> {
  public static final LanguageFolding INSTANCE = new LanguageFolding();

  private LanguageFolding() {
    super("com.intellij.lang.foldingBuilder");
  }
}