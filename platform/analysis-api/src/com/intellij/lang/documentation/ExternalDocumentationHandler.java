// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement additionally in your {@link DocumentationProvider}.
 */
public interface ExternalDocumentationHandler {
  boolean handleExternal(PsiElement element, PsiElement originalElement);
  boolean handleExternalLink(PsiManager psiManager, String link, PsiElement context);
  boolean canFetchDocumentationLink(String link);
  
  @NotNull
  String fetchExternalDocumentation(@NotNull String link, @Nullable PsiElement element);

  /**
   * Defines whether we will show external documentation
   * link at the bottom of the documentation pane or not.
   *
   *
   * @return true if external documentation link should be
   * shown, false otherwise
   */
  default boolean canHandleExternal(@Nullable PsiElement element,
                                    @Nullable PsiElement originalElement) {
    return true;
  }

  /**
   * This method can supply a target (HTML reference), which will be navigated to on showing of
   * {@link #fetchExternalDocumentation(String, PsiElement)}) result.
   *
   * @see com.intellij.codeInsight.documentation.DocumentationManagerProtocol
   */
  @Nullable
  default String extractRefFromLink(@NotNull String link) {
    return null;
  }
}
