// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.model.search.SearchRequest;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Implement this interface and register as {@code com.intellij.psi.symbolReferenceProvider} extension
 * to inject additional references into elements that supports {@link PsiExternalReferenceHost hosting} them.
 * <p/>
 * It's up to host language support to decide what to include into this context (usually it includes string literals),
 * but host elements must be indexed with {@link com.intellij.model.search.SearchContext#IN_CODE_HOSTS host context}
 * in order to be found.
 *
 * @see PsiSymbolReferenceProviderBean
 * @see PsiSymbolReferenceService
 */
public interface PsiSymbolReferenceProvider {

  @NotNull Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost element,
                                                                           @NotNull PsiSymbolReferenceHints hints);

  @NotNull Collection<? extends @NotNull SearchRequest> getSearchRequests(@NotNull Project project, @NotNull Symbol target);
}
