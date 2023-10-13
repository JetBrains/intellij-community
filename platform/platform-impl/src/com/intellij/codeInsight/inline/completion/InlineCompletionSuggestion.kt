// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

open class InlineCompletionSuggestion(val suggestionFlow: Flow<InlineCompletionElement>) : UserDataHolderBase() {
  fun handleInsert() {
    // TODO: add actual insertion here
  }

  companion object {
    val EMPTY: InlineCompletionSuggestion = object : InlineCompletionSuggestion(emptyFlow()) {}
  }
}

