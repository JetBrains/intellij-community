// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.search.SymbolReferenceSearchParameters
import com.intellij.model.search.SymbolReferenceSearcher
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Query

class PsiToSymbolSearcher : SymbolReferenceSearcher {

  override fun collectSearchRequests(parameters: SymbolReferenceSearchParameters): Collection<Query<out SymbolReference>> {
    val symbol: Symbol = parameters.symbol
    val psiElement: PsiElement? = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
    if (psiElement == null) {
      // the Symbol is unrelated to PSI => it cannot have old references
      return emptyList()
    }
    else {
      return listOf(ReferencesSearch.search(ReferencesSearch.SearchParameters(
        psiElement,
        parameters.searchScope,
        false
      )))
    }
  }
}
