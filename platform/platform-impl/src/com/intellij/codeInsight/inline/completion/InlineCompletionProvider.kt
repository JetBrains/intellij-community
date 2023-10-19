// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/**
 * Proposals provider for inline completion.
 *
 * The [getSuggestion] method should return a [Flow] of [InlineCompletionElement] objects representing the proposals for completion.
 * The [isEnabled] method should be implemented to control whether the provider should be called for a particular [InlineCompletionEvent].
 *
 * #### Things to note:
 *   - Currently delay ([Flow.debounce]) for [InlineCompletionEvent.DocumentChange] is disabled and might be implemented later.
 *   You may implement it on provider side or use [DebouncedInlineCompletionProvider] as a entrypoint,
 *   otherwise, proposals will be generated/canceled on each typing.
 *   - Any inline completion request will be cancelled if inline is in rendering mode
 *   - In case a newer inline completion proposals are generated, previous call will be cancelled and hidden
 *   - If some event requires hiding of shown elements, implement [restartOn].
 *
 *
 * @see InlineCompletionElement
 * @see InlineCompletionRequest
 * @see InlineCompletionEvent
 */
interface InlineCompletionProvider {
  /**
   * Provider identifier, must match extension `id` in `plugin.xml`. Used to identify provider.
   *
   * - Prefer to use class qualified name as id to avoid duplicates
   *
   * ```
   * <inline.completion.provider id="<id_here>" .../>
   * ```
   */
  val id: InlineCompletionProviderID

  suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion

  fun isEnabled(event: InlineCompletionEvent): Boolean

  fun restartOn(event: InlineCompletionEvent): Boolean = false

  companion object {
    val EP_NAME = ExtensionPointName.create<InlineCompletionProvider>("com.intellij.inline.completion.provider")
    fun extensions(): List<InlineCompletionProvider> = EP_NAME.extensionList
  }

  object DUMMY : InlineCompletionProvider {
    override val id = InlineCompletionProviderID("DUMMY")
    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion = InlineCompletionSuggestion.EMPTY
    override fun isEnabled(event: InlineCompletionEvent): Boolean = false
  }
}

@JvmInline
value class InlineCompletionProviderID(val id: String)
