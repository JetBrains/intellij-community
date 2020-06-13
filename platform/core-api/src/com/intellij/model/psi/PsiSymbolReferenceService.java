// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Entry point for obtaining {@link PsiSymbolReference}s from {@link PsiElement}.
 */
public interface PsiSymbolReferenceService {

  @NotNull
  static PsiSymbolReferenceService getService() {
    return ServiceManager.getService(PsiSymbolReferenceService.class);
  }

  /**
   * @return all (own and external) references from this element
   */
  @NotNull
  Iterable<? extends PsiSymbolReference> getReferences(@NotNull PsiElement element);

  /**
   * @param <T> type of desired reference
   * @return all (own and external) references from this element, which have desired type
   */
  <@NotNull T extends PsiSymbolReference> @NotNull Collection<T> getReferences(@NotNull PsiElement host, @NotNull Class<T> referenceClass);

  /**
   * @return all (own and external) references from this element, which match {@code hints}
   */
  @NotNull
  Collection<? extends PsiSymbolReference> getReferences(@NotNull PsiElement element, @NotNull PsiSymbolReferenceHints hints);

  /**
   * @return external references from this element, which match {@code hints}
   */
  @NotNull
  Iterable<? extends PsiSymbolReference> getExternalReferences(@NotNull PsiExternalReferenceHost host,
                                                               @NotNull PsiSymbolReferenceHints hints);
}
