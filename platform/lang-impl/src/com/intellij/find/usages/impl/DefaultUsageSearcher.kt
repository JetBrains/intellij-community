// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.find.usages.symbol.SymbolSearchTarget
import com.intellij.model.Symbol
import com.intellij.model.search.SearchService
import com.intellij.util.Query

/**
 * Delegates [Usage] search to the symbol reference search
 * for [SearchTarget]s which specify the [Symbol] for such delegation.
 */
internal class DefaultUsageSearcher : UsageSearcher {

  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
    val target: SearchTarget = parameters.target
    val symbol: Symbol = symbolToSearchForReferences(target) ?: return emptyList()
    val usageQuery: Query<Usage> = SearchService.getInstance()
      .searchPsiSymbolReferences(parameters.project, symbol, parameters.searchScope)
      .mapping {
        TextUsage(it.element, it.rangeInElement)
      }
    return listOf(usageQuery)
  }

  private fun symbolToSearchForReferences(target: SearchTarget): Symbol? {
    return when (target) {
      is SymbolSearchTarget -> target.symbol
      is Symbol -> target
      else -> return null
    }
  }
}
