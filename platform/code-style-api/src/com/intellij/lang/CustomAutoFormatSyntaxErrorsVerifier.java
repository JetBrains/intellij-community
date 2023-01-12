// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * This extension point provides ability to register custom syntax errors verifiers that is used by Reformat Code Action
 * to check that the given PSI doesn't contain any errors
 */
public interface CustomAutoFormatSyntaxErrorsVerifier {
  ExtensionPointName<CustomAutoFormatSyntaxErrorsVerifier> EP_NAME =
    ExtensionPointName.create("com.intellij.lang.formatter.syntaxErrorsVerifier");

  /**
   * This method is called before {@code checkValid()} to ensure that current syntax errors verifier is applicable for the given file
   *
   * @param context the given PSI element to reformat
   * @return {@code true} if the current syntax errors verifier applicable for given context, {@code false} otherwise
   */
  boolean isApplicable(@NotNull PsiElement context);

  /**
   * This method checks and ensures that given context element has no any errors
   *
   * @param context the given PSI element to reformat
   * @return {@code true} if context doesn't contain any syntax errors, {@code false} otherwise
   */
  boolean checkValid(@NotNull PsiElement context);
}
