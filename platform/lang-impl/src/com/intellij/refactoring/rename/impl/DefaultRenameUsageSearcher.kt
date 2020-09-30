// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.search.SearchService
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.refactoring.rename.symbol.ReferenceRenameUsageFactory
import com.intellij.refactoring.rename.symbol.RenameableReference
import com.intellij.refactoring.rename.symbol.SymbolRenameTarget
import com.intellij.util.Query

/**
 * Delegates [RenameUsage] search to the symbol reference search
 * for [RenameTarget]s which specify the [Symbol] for such delegation.
 */
internal class DefaultRenameUsageSearcher : RenameUsageSearcher {

  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val symbol: Symbol = symbolToSearchForReferences(parameters.target) ?: return emptyList()
    val usageQuery: Query<RenameUsage> = SearchService.getInstance()
      .searchPsiSymbolReferences(parameters.project, symbol, parameters.searchScope)
      .mapping(::referenceRenameUsage)
    return listOf(usageQuery)
  }

  private fun symbolToSearchForReferences(target: RenameTarget): Symbol? {
    return when (target) {
      is SymbolRenameTarget -> target.symbol
      is Symbol -> target
      else -> return null
    }
  }

  private fun referenceRenameUsage(reference: PsiSymbolReference): RenameUsage {
    for (factory: ReferenceRenameUsageFactory in ReferenceRenameUsageFactory.EP_NAME.extensions) {
      return factory.renameUsage(reference) ?: continue
    }
    if (reference is RenameableReference) {
      return reference.renameUsage
    }
    if (reference is RenameUsage) {
      return reference
    }
    return DefaultReferenceUsage(reference.element.containingFile, reference.absoluteRange)
  }
}
