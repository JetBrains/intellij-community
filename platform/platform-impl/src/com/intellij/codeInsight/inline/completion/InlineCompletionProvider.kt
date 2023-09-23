// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Proposals provider for inline completion.
 *
 * The [getProposals] method should return a [Flow] of [InlineCompletionElement] objects representing the proposals for completion.
 * The [isEnabled] method should be implemented to control whether the provider should be called for a particular [InlineCompletionEvent].
 *
 * #### Things to note:
 *   - Currently delay ([Flow.debounce]) for [InlineCompletionEvent.DocumentChange] is disabled and might be implemented later.
 *   You may implement it on provider side or use [DebouncedInlineCompletionProvider] as a entrypoint,
 *   otherwise, proposals will be generated/canceled on each typing.
 *   - Any inline completion request will be cancelled if inline is in rendering mode
 *   - In case a newer inline completion proposals are generated, previous call will be cancelled and hidden
 *   - If some event requires hiding of shown elements, implement [requiresInvalidation].
 *
 *
 * @see InlineCompletionElement
 * @see InlineCompletionRequest
 * @see InlineCompletionEvent
 * @see InlineCompletionPlaceholder
 */
@ApiStatus.Experimental
interface InlineCompletionProvider {
  suspend fun getProposals(request: InlineCompletionRequest): Flow<InlineCompletionElement>

  fun isEnabled(event: InlineCompletionEvent): Boolean

  fun requiresInvalidation(event: InlineCompletionEvent): Boolean = false

  companion object {
    val EP_NAME = ExtensionPointName.create<InlineCompletionProvider>("com.intellij.inline.completion.provider")
    fun extensions(): List<InlineCompletionProvider> = EP_NAME.extensionList

    @TestOnly
    fun testExtension(): List<InlineCompletionProvider> = EP_NAME.extensionList
  }

  object DUMMY : InlineCompletionProvider {
    override suspend fun getProposals(request: InlineCompletionRequest): Flow<InlineCompletionElement> = emptyFlow()
    override fun isEnabled(event: InlineCompletionEvent): Boolean = false
  }
}

