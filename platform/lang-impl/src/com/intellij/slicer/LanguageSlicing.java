// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class LanguageSlicing extends LanguageExtension<SliceLanguageSupportProvider> {
  public static final LanguageSlicing INSTANCE = new LanguageSlicing();

  private LanguageSlicing() {
    super("com.intellij.lang.sliceProvider");
  }

  static boolean hasAnyProviders() {
    return INSTANCE.hasAnyExtensions();
  }

  public static SliceLanguageSupportProvider getProvider(@NotNull PsiElement element) {
    return INSTANCE.forLanguage(element.getLanguage());
  }
}