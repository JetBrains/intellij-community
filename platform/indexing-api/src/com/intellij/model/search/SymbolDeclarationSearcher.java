// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolDeclaration;

/**
 * Convenience interface for requestors providing additional queries to find {@link SymbolDeclaration}s by {@link Symbol}.
 */
public interface SymbolDeclarationSearcher extends Searcher<SymbolDeclarationSearchParameters, SymbolDeclaration> {
}
