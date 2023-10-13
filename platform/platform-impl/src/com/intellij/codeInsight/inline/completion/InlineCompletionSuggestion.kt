// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface InlineCompletionSuggestionI {
  val suggestionFlow: Flow<InlineCompletionElement>
}

interface InlineCompletionSuggestionConfigurables {
  val useCache: Boolean
  val addLoadingIndicator: Boolean
  // val renderInBatch: Boolean
}

// TODO: split with interface
open class InlineCompletionSuggestion(override val suggestionFlow: Flow<InlineCompletionElement>)
  : UserDataHolderBase(), InlineCompletionSuggestionI, InlineCompletionSuggestionConfigurables {
  override val useCache: Boolean = true
  override val addLoadingIndicator: Boolean = false

  fun handleInsert() {
    // TODO: add actual insertion here
  }

  companion object {
    val EMPTY: InlineCompletionSuggestion = object : InlineCompletionSuggestion(emptyFlow()) {}
  }
}