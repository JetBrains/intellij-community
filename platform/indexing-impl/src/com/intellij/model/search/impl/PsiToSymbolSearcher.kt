// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.search.PsiSymbolReferenceSearchParameters
import com.intellij.model.search.PsiSymbolReferenceSearcher
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Query

class PsiToSymbolSearcher : PsiSymbolReferenceSearcher {

  override fun collectSearchRequests(parameters: PsiSymbolReferenceSearchParameters): Collection<Query<out PsiSymbolReference>> {
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
