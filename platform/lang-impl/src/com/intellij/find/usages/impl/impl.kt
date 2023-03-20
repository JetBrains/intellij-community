// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.*
import com.intellij.find.usages.symbol.SearchTargetSymbol
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.model.search.impl.buildTextUsageQuery
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClassExtension
import com.intellij.psi.PsiFile
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus
import java.util.*
import com.intellij.usages.Usage as UVUsage

@ApiStatus.Internal
fun searchTargets(file: PsiFile, offset: Int): List<SearchTarget> {
  val targetSymbols = targetSymbols(file, offset)
  if (targetSymbols.isEmpty()) {
    return emptyList()
  }
  return symbolSearchTargets(file.project, targetSymbols)
}

internal fun symbolSearchTargets(project: Project, targetSymbols: Collection<Symbol>): List<SearchTarget> {
  return targetSymbols.mapNotNullTo(LinkedHashSet()) {
    symbolSearchTarget(project, it)
  }.toList()
}

private val SYMBOL_SEARCH_TARGET_EXTENSION = ClassExtension<SymbolSearchTargetFactory<*>>("com.intellij.lang.symbolSearchTarget")

@ApiStatus.Internal
fun symbolSearchTarget(project: Project, symbol: Symbol): SearchTarget? {
  for (factory in SYMBOL_SEARCH_TARGET_EXTENSION.forKey(symbol.javaClass)) {
    @Suppress("UNCHECKED_CAST")
    val factory_ = factory as SymbolSearchTargetFactory<Symbol>
    val target = factory_.searchTarget(project, symbol)
    if (target != null) {
      return target
    }
  }
  if (symbol is SearchTargetSymbol) {
    return symbol.searchTarget
  }
  if (symbol is SearchTarget) {
    return symbol
  }
  return null
}

@ApiStatus.Internal
fun buildUsageViewQuery(
  project: Project,
  target: SearchTarget,
  allOptions: AllSearchOptions,
): Query<out UVUsage> {
  return buildQuery(project, target, allOptions).transforming {
    if (it is PsiUsage && !it.declaration) {
      listOf(Psi2UsageInfo2UsageAdapter(PsiUsage2UsageInfo(it)))
    }
    else {
      emptyList()
    }
  }
}

@ApiStatus.Internal
fun buildQuery(
  project: Project,
  target: SearchTarget,
  allOptions: AllSearchOptions,
): Query<out Usage> {
  val queries = ArrayList<Query<out Usage>>()
  val (options, textSearch) = allOptions
  if (options.isUsages) {
    queries += SearchService.getInstance().searchParameters(DefaultUsageSearchParameters(project, target, options.searchScope))
  }
  if (textSearch == true) {
    target.textSearchRequests.mapTo(queries) { searchRequest ->
      buildTextUsageQuery(project, searchRequest, options.searchScope, textSearchContexts).mapping(::PlainTextUsage)
    }
  }
  return SearchService.getInstance().merge(queries)
}

private class DefaultUsageSearchParameters(
  private val project: Project,
  target: SearchTarget,
  override val searchScope: SearchScope
) : UsageSearchParameters {
  private val pointer: Pointer<out SearchTarget> = target.createPointer()
  override fun areValid(): Boolean = pointer.dereference() != null
  override fun getProject(): Project = project
  override val target: SearchTarget get() = requireNotNull(pointer.dereference())
}

private val textSearchContexts: Set<SearchContext> = EnumSet.of(
  SearchContext.IN_COMMENTS, SearchContext.IN_STRINGS,
  SearchContext.IN_PLAIN_TEXT
)

internal fun SearchTarget.hasTextSearchStrings(): Boolean = textSearchRequests.isNotEmpty()
