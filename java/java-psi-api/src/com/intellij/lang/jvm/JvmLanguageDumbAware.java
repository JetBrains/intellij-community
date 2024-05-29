// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * JvmLanguageDumbAware is an interface that represents a language extension that indicates that language supports dumb mode.
 * It provides methods to check whether a given PsiElement is supported in dumb mode.
 */
@ApiStatus.Experimental
public interface JvmLanguageDumbAware {
  LanguageExtension<JvmLanguageDumbAware> EP = new LanguageExtension<>("com.intellij.lang.dumb.mode.supported");

  /**
   * Checks whether the given PsiElement can be used in dumb mode.
   * Can be called either in dumb mode or usual mode.
   * Usually, it is used to check language only.
   *
   * @param psiElement the PsiElement to be checked.
   * @return true if the PsiElement is supported, false otherwise.
   */
  boolean supportDumbMode(@NotNull PsiElement psiElement);

  /**
   * Checks whether the given PsiClass can be used in dumb mode.
   * Can be called either in dumb mode or usual mode.
   * Usually, it is used to check language only
   *
   * @param psiElement the PsiElement to be checked.
   * @return true if the PsiElement is supported, false otherwise.
   */
  static boolean isSupported(@NotNull PsiElement psiElement) {
    for (JvmLanguageDumbAware languageDumbAware : EP.allForLanguage(psiElement.getLanguage())) {
      if (languageDumbAware.supportDumbMode(psiElement)) {
        return true;
      }
    }
    return false;
  }
}
