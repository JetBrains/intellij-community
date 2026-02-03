// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface TypeDeclarationProvider {
  ExtensionPointName<TypeDeclarationProvider> EP_NAME = ExtensionPointName.create("com.intellij.typeDeclarationProvider");

  /**
   * Provides an array of declarations of type of given {@code symbol}.
   *
   * @param symbol input PSI element
   * @return all target declarations as an array of {@code PsiElement} or {@code null} if none were found
   */
  PsiElement @Nullable [] getSymbolTypeDeclarations(@NotNull PsiElement symbol);

  /**
   * Provides the custom action text.
   *
   * @param context the action data context.
   * @return the custom text or {@code null} to use the default text.
   */
  default @Nullable @Nls(capitalization = Nls.Capitalization.Title) String getActionText(@NotNull DataContext context) {
    return null;
  }
}
