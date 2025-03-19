// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

// The workaround because the implementation depends on the lang-impl module
@ApiStatus.Experimental
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface InlineCompletionPartialAcceptHandler {

  @RequiresEdt
  @RequiresWriteLock
  fun insertNextWord(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement>

  @RequiresEdt
  @RequiresWriteLock
  fun insertNextLine(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement>

  companion object {
    private val EP = ExtensionPointName.create<InlineCompletionPartialAcceptHandler>("com.intellij.inline.completion.partial.accept.handler")

    internal fun get(): InlineCompletionPartialAcceptHandler {
      val extensions = EP.extensionList
      check(extensions.isNotEmpty()) { "Cannot find the handler in Inline Completion for partial acceptance." }
      check(extensions.size == 1) { "InlineCompletionPartialAcceptHandler cannot be extended outside." }
      return extensions.single()
    }
  }
}
