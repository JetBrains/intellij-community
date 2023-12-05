// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Abstract class representing an inline completion suggestion.
 *
 * Provides the suggestion flow for generating only one suggestion.
 * @see InlineCompletionElement
 */
abstract class InlineCompletionSuggestion : UserDataHolderBase() {
  abstract val suggestionFlow: Flow<InlineCompletionElement>

  class Default(override val suggestionFlow: Flow<InlineCompletionElement>) : InlineCompletionSuggestion()

  companion object {
    fun empty(): InlineCompletionSuggestion = Default(emptyFlow())

    fun withFlow(buildSuggestion: suspend FlowCollector<InlineCompletionElement>.() -> Unit): InlineCompletionSuggestion {
      return Default(flow(buildSuggestion))
    }
  }
}

