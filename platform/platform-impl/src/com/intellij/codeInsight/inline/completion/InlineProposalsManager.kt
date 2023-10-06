// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.render.InlineCompletionBlock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class InlineProposalsManager {
  private val myProposals = arrayListOf<CacheableInlineProposal>()
  private var currentIndex = 0
  private val current
    get() = myProposals.getOrNull(currentIndex)

  internal fun clear() {
    myProposals.clear()
    currentIndex = 0
  }

  internal fun processEvent(event: InlineCompletionEvent) {
    when (event) {
      is InlineCompletionEvent.ShowNext -> currentIndex = minOf(currentIndex + 1, myProposals.size)
      is InlineCompletionEvent.ShowPrevious -> currentIndex = maxOf(currentIndex - 1, 0)
      else -> {
        clear()
        myProposals.addAll(InlineCompletionProvider.extensions().filter { it.isEnabled(event) }.map(::CacheableInlineProposal))
      }
    }
  }

  internal fun getProvider(): InlineCompletionProvider? = current?.provider

  internal fun cacheProposal(proposal: List<InlineCompletionBlock>) {
    if (proposal.isNotEmpty()) {
      current?.apply { cachedProposal = proposal.map(InlineCompletionBlock::withSameContent) }
    }
  }

  internal fun getCachedProposal(): List<InlineCompletionBlock>? = current?.cachedProposal
}

@ApiStatus.Experimental
private data class CacheableInlineProposal(val provider: InlineCompletionProvider) {
  var cachedProposal: List<InlineCompletionBlock>? = null
}