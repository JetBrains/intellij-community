// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion.Companion.MAX_VARIANTS_NUMBER
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector


/**
 * The `InlineCompletionSuggestion` interface provides a contract for retrieving inline completion variants.
 *
 * The flow of suggestion is as follows:
 * * The [InlineCompletionHandler] asks a provider all the variants via [getVariants].
 * * After that, it starts computing them **one by one** (at least for now) on the background thread.
 * * If some event happens during presence of [InlineCompletionSuggestion], [InlineCompletionSuggestionUpdateManager] is used.
 * * There is a limitation to the number of variants: [MAX_VARIANTS_NUMBER].
 *
 * @see InlineCompletionVariant
 * @see InlineCompletionSession.useNextVariant
 * @see InlineCompletionSession.usePrevVariant
 * @see InlineCompletionSession.capture
 * @see InlineCompletionSingleSuggestion
 * @see InlineCompletionSuggestionBuilder
 */
interface InlineCompletionSuggestion {

  /**
   * @see InlineCompletionVariant
   */
  suspend fun getVariants(): List<InlineCompletionVariant>

  object Empty : InlineCompletionSuggestion {
    override suspend fun getVariants(): List<InlineCompletionVariant> = emptyList()
  }

  companion object {
    const val MAX_VARIANTS_NUMBER = 20
  }
}

interface InlineCompletionSingleSuggestion : InlineCompletionSuggestion {
  suspend fun getVariant(): InlineCompletionVariant

  override suspend fun getVariants(): List<InlineCompletionVariant> {
    return listOf(getVariant())
  }

  companion object {

    /**
     * @see [InlineCompletionVariant.build]
     */
    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      buildElements: suspend FlowCollector<InlineCompletionElement>.(data: UserDataHolderBase) -> Unit
    ): InlineCompletionSingleSuggestion {
      return object : InlineCompletionSingleSuggestion {
        override suspend fun getVariant(): InlineCompletionVariant = InlineCompletionVariant.build(data, buildElements)
      }
    }

    /**
     * @see InlineCompletionVariant.build
     */
    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      elements: Flow<InlineCompletionElement>
    ): InlineCompletionSingleSuggestion {
      return object : InlineCompletionSingleSuggestion {
        override suspend fun getVariant(): InlineCompletionVariant = InlineCompletionVariant.build(data, elements)
      }
    }
  }
}
