// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Entry point for obtaining {@link PsiSymbolReference}s from {@link PsiElement}.
 */
public interface PsiSymbolReferenceService {

  static @NotNull PsiSymbolReferenceService getService() {
    return ApplicationManager.getApplication().getService(PsiSymbolReferenceService.class);
  }

  /**
   * @return all (own and external) references from this element
   */
  @NotNull @Unmodifiable
  Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull PsiElement element);

  /**
   * @param <T> type of desired reference
   * @return all (own and external) references from this element, which have desired type
   */
  <T extends @NotNull PsiSymbolReference> @NotNull @Unmodifiable Collection<T> getReferences(@NotNull PsiElement host, @NotNull Class<T> referenceClass);

  /**
   * @return all (own and external) references from this element, which match {@code hints}
   */
  @NotNull @Unmodifiable Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull PsiElement element,
                                                                           @NotNull PsiSymbolReferenceHints hints);

  /**
   * @return external references from this element, which match {@code hints}
   */
  @NotNull @Unmodifiable
  Collection<? extends @NotNull PsiSymbolReference> getExternalReferences(@NotNull PsiExternalReferenceHost host,
                                                                          @NotNull PsiSymbolReferenceHints hints);
}
