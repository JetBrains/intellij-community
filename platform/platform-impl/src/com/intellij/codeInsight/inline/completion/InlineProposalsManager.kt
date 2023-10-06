// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class InlineProposalsManager : ArrayList<CacheableInlineProposal>() {
  private var current: Int = 0

  override fun clear() {
    super.clear()
    current = 0
  }

  internal fun processEvent(event: InlineCompletionEvent) {
    if (event is InlineCompletionEvent.ShowNext) {
      if (current + 1 < size) {
        ++current
      }
      return
    }
    if (event is InlineCompletionEvent.ShowPrevious) {
      if (current > 0) {
        --current
      }
      return
    }
    clear()
    addAll(InlineCompletionProvider.extensions().filter { it.isEnabled(event) }.map(::CacheableInlineProposal))
  }

  internal fun getProvider(): InlineCompletionProvider? = getOrNull(current)?.provider

  internal fun cacheProposal(proposal: String) {
    getOrNull(current)?.apply { cachedProposal = InlineCompletionElement(proposal) }
  }

  internal fun getCachedProposal(): InlineCompletionElement? = getOrNull(current)?.cachedProposal
}

@ApiStatus.Experimental
internal data class CacheableInlineProposal(val provider: InlineCompletionProvider) {
  var cachedProposal: InlineCompletionElement? = null
}