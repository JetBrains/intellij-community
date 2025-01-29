// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.find.usages.api.*
import com.intellij.find.usages.impl.registerSymbolSearchTargetFactoryForTesting
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.registerSearcherForTesting
import com.intellij.testFramework.registerExtension
import com.intellij.util.Query
import com.intellij.util.application

internal fun registerTestSymbolAndSearchTarget(
  testRootDisposable: Disposable,
  hostLanguage: String,
  hostElementClass: String,
) {
  run {
    val bean = PsiSymbolReferenceProviderBean().apply {
      implementationClass = TestReferenceProvider::class.java.name
      targetClass = TestSymbol::class.java.name
      this.hostLanguage = hostLanguage
      this.hostElementClass = hostElementClass
      this.pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
    }
    val ep = ExtensionPointName<PsiSymbolReferenceProviderBean>("com.intellij.psi.symbolReferenceProvider")
    application.registerExtension(ep, bean, testRootDisposable)
  }

  registerSymbolSearchTargetFactoryForTesting(
    key = TestSymbol::class.java,
    factory = TestSearchTargetFactory(),
    disposable = testRootDisposable
  )

  registerSearcherForTesting(
    key = UsageSearchParameters::class.java,
    searcher = TestSearcher(),
    parentDisposable = testRootDisposable
  )
}

private class TestReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    return listOf(TestReference(element))
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    if (target !is TestSymbol) return emptyList()
    return listOf(SearchRequest.of(search_word))
  }
}

private class TestReference(val psi: PsiElement) : PsiSymbolReference {

  override fun getElement(): PsiElement = psi

  override fun getRangeInElement(): TextRange = TextRange(0, psi.textLength)

  override fun resolveReference(): Collection<Symbol> = listOf(TestSymbol())
}

private class TestSymbol : Symbol, Pointer<TestSymbol> {
  override fun createPointer(): Pointer<out Symbol> = this
  override fun dereference(): TestSymbol = this
}

private class TestSearchTargetFactory : SymbolSearchTargetFactory<TestSymbol> {
  override fun searchTarget(project: Project, symbol: TestSymbol): SearchTarget = TestSearchTarget(symbol)
}

private class TestSearchTarget(val symbol: TestSymbol) : SearchTarget, Pointer<TestSearchTarget> {
  override fun createPointer(): TestSearchTarget = this
  override fun dereference(): TestSearchTarget = this

  override fun presentation(): TargetPresentation = TargetPresentation.builder("test").presentation()

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler("test")

  override fun equals(other: Any?): Boolean =
    other === this || other is TestSearchTarget && other.symbol == symbol

  override fun hashCode(): Int = symbol.hashCode()
}

private class TestSearcher : UsageSearcher {
  override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage>? {
    if (parameters.target !is TestSearchTarget) return null

    return SearchService.getInstance()
      .searchWord(parameters.project, search_word)
      .caseSensitive(false)
      .inContexts(SearchContext.inComments())
      .inScope(parameters.searchScope)
      .buildQuery {
        listOf(TestUsage(it.scope.containingFile, it.start.textRange))
      }
  }
}

private class TestUsage(override val file: PsiFile, override val range: TextRange) : PsiUsage, Pointer<TestUsage> {
  override fun createPointer(): TestUsage = this
  override fun dereference(): TestUsage = this

  override val declaration: Boolean get() = false
}

private const val search_word = "targets"
