// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.lang.LanguageExtension;

/**
 * @author yole
 */
public final class LanguageFileViewProviders extends LanguageExtension<FileViewProviderFactory> {
  public static final LanguageFileViewProviders INSTANCE = new LanguageFileViewProviders();

  private LanguageFileViewProviders() {
    super("com.intellij.lang.fileViewProviderFactory");
  }
}