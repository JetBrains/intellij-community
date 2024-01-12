// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

// TODO docs
interface InlineCompletionSuggestion {

  suspend fun getVariants(): List<InlineCompletionVariant>

  object Empty : InlineCompletionSuggestion {
    override suspend fun getVariants(): List<InlineCompletionVariant> = emptyList()
  }

  companion object
}

interface InlineCompletionSingleSuggestion : InlineCompletionSuggestion {
  suspend fun getVariant(): InlineCompletionVariant

  override suspend fun getVariants(): List<InlineCompletionVariant> {
    return listOf(getVariant())
  }

  companion object {

    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      buildElements: suspend FlowCollector<InlineCompletionElement>.(data: UserDataHolderBase) -> Unit
    ): InlineCompletionSingleSuggestion {
      return object : InlineCompletionSingleSuggestion {
        override suspend fun getVariant(): InlineCompletionVariant = InlineCompletionVariant.build(data, buildElements)
      }
    }

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
