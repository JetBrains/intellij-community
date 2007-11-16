package com.intellij.psi;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public class LanguageFileViewProviders extends LanguageExtension<FileViewProviderFactory> {
  public static final LanguageFileViewProviders INSTANCE = new LanguageFileViewProviders();

  private LanguageFileViewProviders() {
    super("com.intellij.lang.fileViewProviderFactory");
  }
}