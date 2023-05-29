// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Proposals provider for inline completion.
 * Implement [getProposals] method to return a list of proposals and [isEnabled] to control if a provider need to be called.
 * Inline completion has a user type delay. By default, it's 300ms. You can change it by modifying `inline.completion.listener` registry value (@see [InlineCompletionDocumentListener])
 */
@ApiStatus.Experimental
interface InlineCompletionProvider {
  suspend fun getProposals(request: InlineCompletionRequest): List<InlineCompletionElement>

  fun isEnabled(event: DocumentEvent): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionProvider>("com.intellij.inline.completion.provider")
    fun extensions() = EP_NAME.extensionList
  }

  object DUMMY : InlineCompletionProvider {
    override suspend fun getProposals(request: InlineCompletionRequest) = emptyList<InlineCompletionElement>()
    override fun isEnabled(event: DocumentEvent) = true
  }
}
