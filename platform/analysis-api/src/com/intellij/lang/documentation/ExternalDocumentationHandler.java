// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement additionally in your {@link DocumentationProvider}.
 */
public interface ExternalDocumentationHandler {

  default boolean handleExternal(PsiElement element, PsiElement originalElement) {
    return false;
  }

  default boolean handleExternalLink(PsiManager psiManager, String link, PsiElement context) {
    return false;
  }

  default boolean canFetchDocumentationLink(String link) {
    return false;
  }

  default @Nls @NotNull String fetchExternalDocumentation(@NotNull String link, @Nullable PsiElement element) {
    return "";
  }

  /**
   * Defines whether we will show external documentation
   * link at the bottom of the documentation pane or not.
   *
   * @return true if external documentation link should be
   * shown, false otherwise
   */
  default boolean canHandleExternal(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    return true;
  }

  /**
   * This method can supply a target (HTML reference), which will be navigated to on showing of
   * {@link #fetchExternalDocumentation(String, PsiElement)}) result.
   *
   * @see com.intellij.codeInsight.documentation.DocumentationManagerProtocol
   */
  default @Nullable String extractRefFromLink(@NotNull String link) {
    return null;
  }
}
