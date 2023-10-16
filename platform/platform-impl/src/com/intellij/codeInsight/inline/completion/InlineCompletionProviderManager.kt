// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.errorIfNotMessage

@ApiStatus.Experimental
class InlineCompletionProviderManager {
  internal var source: InlineCompletionEvent? = null

  /**
   * Allows to cache multiple suggestions per multiple providers
   *  - [suggestions] - collection of cached suggestions
   *  - [currentIndexes] - collection of indexes to last used cached suggestion
   *
   *  Temporary works for one provider only due to API limitations //TODO: add task
   */
  private val suggestions = mutableMapOf<InlineCompletionProviderID, MutableList<List<InlineCompletionElement>>>()
  private var currentIndexes = mutableMapOf<InlineCompletionProviderID, Int>()

  private var providerIndex: Int = 0

  internal fun cacheSuggestion(providerId: InlineCompletionProviderID, elements: List<InlineCompletionElement.Presentable>) {
    if (suggestions.containsKey(providerId)) {
      val cachesPerProvider = suggestions.getValue(providerId)
      cachesPerProvider.add(elements.map { it.element.withSameContent() })
      currentIndexes[providerId] = cachesPerProvider.size - 1
    }
    else {
      suggestions[providerId] = mutableListOf(elements.map { it.element.withSameContent() })
      currentIndexes[providerId] = 0
    }
  }

  internal fun getCache(providerId: InlineCompletionProviderID): List<InlineCompletionElement.Presentable>? {
    val index = currentIndexes[providerId] ?: return null
    val suggestion = suggestions[providerId] ?: return null
    return suggestion.getOrNull(index)?.map { it.toPresentable() }
  }

  internal fun clear() {
    source = null
    suggestions.clear()
    currentIndexes.clear()
  }

  internal fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    source = event
    if (application.isUnitTestMode && testProvider != null) {
      return testProvider
    }
    val providers = InlineCompletionProvider.extensions()

    if (event is InlineCompletionEvent.Navigation) {
      return when (event) {
        is InlineCompletionEvent.Navigation.NextSuggestion -> TODO("Case with multiple suggestions per provider is not supported yet")
        is InlineCompletionEvent.Navigation.PrevSuggestion -> TODO("Case with multiple suggestions per provider is not supported yet")
        is InlineCompletionEvent.Navigation.NextProvider -> {
          // if provider is next => skipping previous N already checked providers and find first suitable one.
          val previous = providerIndex + 1
          providerIndex = providers.drop(previous).findProvider(event.source)?.let { it + previous } ?: return null
          providers[providerIndex]
        }
        is InlineCompletionEvent.Navigation.PrevProvider -> {
          // if provider is previous => skipping last K not yet checked providers, reverse previous ones and find first suitable one.
          providerIndex = providers.take(providerIndex).reversed().findProvider(event.source)
                            ?.let { providerIndex - it - 1 } ?: return null
          providers[providerIndex]
        }
      }
    }

    providerIndex = providers.findProvider(event) ?: return null
    return providers[providerIndex]
  }

  private fun List<InlineCompletionProvider>.findProvider(event: InlineCompletionEvent) = indexOfFirst {
    try {
      it.isEnabled(event)
    }
    catch (e: Throwable) {
      LOG.errorIfNotMessage(e)
      false
    }
  }.takeIf { it >= 0 }

  companion object {
    private val LOG = thisLogger()

    private var testProvider: InlineCompletionProvider? = null

    @TestOnly
    fun registerTestHandler(provider: InlineCompletionProvider) {
      testProvider = provider
    }

    @TestOnly
    fun unRegisterTestHandler() {
      testProvider = null
    }
  }
}
