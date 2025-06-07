// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class CorrectionWrapper<I : Any>(
  private val delegate: WeightedSearchEverywhereContributor<I>,
  val correction: SearchEverywhereSpellCheckResult,
) : WeightedSearchEverywhereContributor<I> by delegate,
    SearchEverywhereContributorWrapper {

  private companion object {
    private const val PAGE_SIZE = 10
  }

  private var currentLimit = PAGE_SIZE
  fun getLimit(): Int = currentLimit
  fun increaseAndGetLimit(): Int {
    currentLimit += PAGE_SIZE
    return currentLimit
  }


  override fun getEffectiveContributor(): SearchEverywhereContributor<*> = delegate
  override fun getSearchProviderId(): String = delegate.searchProviderId
  override fun getFullGroupName(): @Nls String {
    return delegate.fullGroupName
  }

  override fun isShownInSeparateTab(): Boolean {
    return delegate.isShownInSeparateTab
  }

  override fun getSupportedCommands(): List<SearchEverywhereCommandInfo?> {
    return delegate.supportedCommands
  }

  override fun getAdvertisement(): @Nls String? {
    return delegate.advertisement
  }

  override fun getActions(onChanged: Runnable): List<AnAction?> {
    return delegate.getActions(onChanged)
  }

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<I>>,
  ) {
    val query = (correction as? SearchEverywhereSpellCheckResult.Correction)?.correction ?: pattern
    delegate.fetchWeightedElements(query, progressIndicator, consumer)
  }

  override fun processSelectedItem(
    selected: I,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    val query = (correction as? SearchEverywhereSpellCheckResult.Correction)?.correction ?: searchText
    return delegate.processSelectedItem(selected, modifiers, query)
  }

  override fun filterControlSymbols(pattern: String): String {
    return delegate.filterControlSymbols(pattern)
  }

  override fun isMultiSelectionSupported(): Boolean {
    return delegate.isMultiSelectionSupported
  }

  override fun isDumbAware(): Boolean {
    return delegate.isDumbAware
  }

  override fun isEmptyPatternSupported(): Boolean {
    return delegate.isEmptyPatternSupported
  }

  override fun dispose() {}
}
