// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion.Variant
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

// TODO docs
// TODO renaming
interface InlineCompletionSuggestion {

  suspend fun getVariants(): List<Variant>

  val updater: InlineCompletionEventBasedSuggestionUpdater
    get() = InlineCompletionEventBasedSuggestionUpdater.Default.INSTANCE

  interface Variant {

    val data: UserDataHolderBase

    val elements: Flow<InlineCompletionElement>

    private class Impl(
      override val data: UserDataHolderBase,
      override val elements: Flow<InlineCompletionElement>
    ) : Variant

    companion object {

      fun build(
        data: UserDataHolderBase = UserDataHolderBase(),
        buildElements: suspend FlowCollector<InlineCompletionElement>.() -> Unit
      ): Variant {
        return Impl(data, flow(buildElements))
      }

      fun build(
        data: UserDataHolderBase = UserDataHolderBase(),
        elements: Flow<InlineCompletionElement>
      ): Variant {
        return Impl(data, elements)
      }
    }
  }

  // TODO take out from here
  class VariantSnapshot @ApiStatus.Internal constructor(
    val data: UserDataHolderBase,
    val elements: List<InlineCompletionElement>,
    val index: Int,
    val isCurrentlyDisplaying: Boolean
  ) {

    fun copy(elements: List<InlineCompletionElement>): VariantSnapshot {
      return VariantSnapshot(data, elements, index, isCurrentlyDisplaying)
    }
  }

  object Empty : InlineCompletionSuggestion {
    override suspend fun getVariants(): List<Variant> = emptyList()
  }

  companion object
}

// TODO maybe class, not interface
interface InlineCompletionSingleSuggestion : InlineCompletionSuggestion {
  suspend fun getVariant(): Variant

  override suspend fun getVariants(): List<Variant> {
    return listOf(getVariant())
  }

  companion object {

    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      buildElements: suspend FlowCollector<InlineCompletionElement>.() -> Unit
    ): InlineCompletionSingleSuggestion {
      return object : InlineCompletionSingleSuggestion {
        override suspend fun getVariant(): Variant = Variant.build(data, buildElements)
      }
    }

    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      elements: Flow<InlineCompletionElement>
    ): InlineCompletionSingleSuggestion {
      return object : InlineCompletionSingleSuggestion {
        override suspend fun getVariant(): Variant = Variant.build(data, elements)
      }
    }
  }
}
