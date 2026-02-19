// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus


/**
 * Represents a variant of inline completion as a part of [InlineCompletionSuggestion].
 *
 * Each suggestion provides multiple variants that can be switched between each other by a user.
 * A variant can be switched if one of the following conditions is met:
 * * The variant contains at least one computed [InlineCompletionElement].
 * * The variant is still empty and being computed, and all the previous variants are empty and did finish its computations.
 *
 * If the variant meets none of these conditions, it is skipped when navigating.
 *
 * The interface provides access to the data associated with the variant and the elements of the variant.
 *
 * @see InlineCompletionSuggestion
 */
interface InlineCompletionVariant {

  /**
   * [UserDataHolderBase] associated with the variant.
   * It can be accessed via [InlineCompletionSession.capture] or [InlineCompletionSuggestionUpdateManager].
   *
   * Also, if this variant is currently used, the data is synced with [InlineCompletionSession.context].
   */
  val data: UserDataHolderBase

  /**
   * Returns all the [InlineCompletionElement] that should be displayed when the variant is currently used.
   * Since it returns a flow, the inline completion supports gradual appearance of elements.
   */
  val elements: Flow<InlineCompletionElement>

  /**
   * Represents information about a variant at some point of time. Actively used in [InlineCompletionSession.capture] and
   * [InlineCompletionSuggestionUpdateManager].
   *
   * It is valid only at the time you receive it on EDT. It is not invalidated automatically, so you should not store snapshots.
   *
   * When you receive a snapshot and need to return a new one, use [copy].
   */
  class Snapshot @ApiStatus.Internal constructor(
    /**
     * @see InlineCompletionVariant.data
     */
    val data: UserDataHolderBase,
    /**
     * Since it's a snapshot, it represents a current state of elements.
     * @see InlineCompletionVariant.elements
     */
    val elements: List<InlineCompletionElement>,
    /**
     * **Raw** index of this variant. 'Raw' means that it's the index in [InlineCompletionSuggestion]'s list forgetting about the fact
     * that some variants might be empty or already invalided.
     */
    val index: Int,
    /**
     * Whether it's currently used. Only one variant can return `true` here.
     */
    val isActive: Boolean,
    /**
     * Whether the variant is being computed, already computed, invalidated by [InlineCompletionSuggestionUpdateManager] or untouched.
     * 'Untouched' means that it didn't start computing.
     */
    val state: State
  ) {

    /**
     * Whether [elements] is empty.
     */
    @ApiStatus.Experimental
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

    /**
     * Constructor of the variant. Example:
     * ```kotlin
     * return InlineCompletionVariant.build { data ->
     *   ...
     *   data.putUserData(key, value)
     *   emit(InlineCompletionGrayTextElement(text))
     * }
     * ```
     */
    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      buildElements: suspend FlowCollector<InlineCompletionElement>.(data: UserDataHolderBase) -> Unit
    ): InlineCompletionVariant {
      return Impl(data, flow { buildElements(data) })
    }

    /**
     * Constructor of the variant. Example:
     * ```kotlin
     * val data = UserDataHolderBase()
     * val elements: Flow<InlineCompletionElement> = receiveElements(data)
     * return InlineCompletionVariant.build(data, elements)
     * ```
     */
    fun build(
      data: UserDataHolderBase = UserDataHolderBase(),
      elements: Flow<InlineCompletionElement>
    ): InlineCompletionVariant {
      return Impl(data, elements)
    }
  }
}
