// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Restricts applicability of (automatic) code formatter for given PSI context.
 */
public interface LanguageFormattingRestriction {
  ExtensionPointName<LanguageFormattingRestriction> EP_NAME = ExtensionPointName.create(
    "com.intellij.lang.formatter.restriction");

  boolean isFormatterAllowed(@NotNull PsiElement context);

  /**
   * Checks if automatic code reformat is allowed, for example, on save. By default, the method returns the same value as
   * {@link #isFormatterAllowed(PsiElement)} used for explicit reformat.
   *
   * @param context A context element.
   *
   * @return True if automatic reformat is allowed, false to block it. For automatic formatting to work, this method and
   * {@link #isFormatterAllowed(PsiElement)} must <i>both</i> return {@code true}.
   *
   * @see LanguageFormatting#isAutoFormatAllowed(PsiElement)
   */
  default boolean isAutoFormatAllowed(@NotNull PsiElement context) {
    return isFormatterAllowed(context);
  }
}
