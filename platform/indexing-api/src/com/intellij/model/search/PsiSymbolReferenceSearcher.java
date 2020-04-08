// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;

/**
 * Convenience interface for searchers providing additional queries to find {@link PsiSymbolReference}s by {@link Symbol}.
 */
public interface PsiSymbolReferenceSearcher extends Searcher<PsiSymbolReferenceSearchParameters, PsiSymbolReference> {
}
