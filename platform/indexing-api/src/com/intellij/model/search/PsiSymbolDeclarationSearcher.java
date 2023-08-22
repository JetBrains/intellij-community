// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;

/**
 * Convenience interface for searchers providing additional queries to find {@link PsiSymbolDeclaration}s by {@link Symbol}.
 */
@OverrideOnly
public interface PsiSymbolDeclarationSearcher extends Searcher<PsiSymbolDeclarationSearchParameters, PsiSymbolDeclaration> {
}
