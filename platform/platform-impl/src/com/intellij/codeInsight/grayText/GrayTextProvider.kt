// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.grayText

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Proposals provider for gray text completion.
 * Implement [getProposals] method to return a list of proposals and [isEnabled] to control if a provider need to be called.
 * Gray text has a user type delay. By default, it's 300ms. You can change it by modifying `gray.text.listener` registry value (@see [GrayTextDocumentListener])
 */
@ApiStatus.Experimental
interface GrayTextProvider {
  suspend fun getProposals(request: GrayTextRequest): List<GrayTextElement>

  fun isEnabled(event: DocumentEvent): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<GrayTextProvider>("com.intellij.grayText.provider")
    fun extensions() = EP_NAME.extensionList
  }

  object DUMMY : GrayTextProvider {
    override suspend fun getProposals(request: GrayTextRequest) = emptyList<GrayTextElement>()
    override fun isEnabled(event: DocumentEvent) = true
  }
}
