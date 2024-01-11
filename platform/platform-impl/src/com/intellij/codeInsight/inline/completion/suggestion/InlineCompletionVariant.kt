// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

interface InlineCompletionVariant {

  val data: UserDataHolderBase

  val elements: Flow<InlineCompletionElement>

  // TODO docs
  class Snapshot @ApiStatus.Internal constructor(
    val data: UserDataHolderBase,
    val elements: List<InlineCompletionElement>,
    val index: Int,
    val isActive: Boolean,
    val state: State
  ) {

    fun isEmpty(): Boolean = elements.isEmpty()

    fun copy(elements: List<InlineCompletionElement>): Snapshot {
      return Snapshot(data, elements, index, isActive, state)
    }

    enum class State {
      UNTOUCHED,
      IN_PROGRESS,
      COMPUTED,
      INVALIDATED
    }
  }

  companion object {

    private class Impl(
      override val data: UserDataHolderBase,
      override val elements: Flow<InlineCompletionElement>
    ) : InlineCompletionVariant

    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      buildElements: suspend FlowCollector<InlineCompletionElement>.() -> Unit
    ): InlineCompletionVariant {
      return Impl(data, flow(buildElements))
    }

    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      elements: Flow<InlineCompletionElement>
    ): InlineCompletionVariant {
      return Impl(data, elements)
    }
  }
}
