// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class InlineProposalsManager {
  private val myProposals = arrayListOf<CacheableInlineProposal>()
  private var current: Int = 0

  internal fun clear() {
    myProposals.clear()
    current = 0
  }

  internal fun processEvent(event: InlineCompletionEvent) {
    when (event) {
      is InlineCompletionEvent.ShowNext -> current = minOf(current + 1, myProposals.size)
      is InlineCompletionEvent.ShowPrevious -> current = maxOf(current - 1, 0)
      else -> {
        clear()
        myProposals.addAll(InlineCompletionProvider.extensions().filter { it.isEnabled(event) }.map(::CacheableInlineProposal))
      }
    }
  }

  internal fun getProvider(): InlineCompletionProvider? = getCurrent()?.provider

  internal fun cacheProposal(proposal: String) {
    getCurrent()?.apply { cachedProposal = InlineCompletionElement(proposal) }
  }

  internal fun getCachedProposal(): InlineCompletionElement? = getCurrent()?.cachedProposal

  private fun getCurrent() = myProposals.getOrNull(current)
}

@ApiStatus.Experimental
internal data class CacheableInlineProposal(val provider: InlineCompletionProvider) {
  var cachedProposal: InlineCompletionElement? = null
}