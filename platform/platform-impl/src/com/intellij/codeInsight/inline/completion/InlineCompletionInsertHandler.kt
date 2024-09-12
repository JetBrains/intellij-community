// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock

class InlineCompletionInsertEnvironment(
  val editor: Editor,
  val file: PsiFile,
  val insertedRange: TextRange,
) : UserDataHolderBase()

interface InlineCompletionInsertHandler {
  @RequiresEdt
  @RequiresWriteLock
  fun afterInsertion(environment: InlineCompletionInsertEnvironment, elements: List<InlineCompletionElement>)

  object Dummy : InlineCompletionInsertHandler {
    override fun afterInsertion(environment: InlineCompletionInsertEnvironment, elements: List<InlineCompletionElement>) = Unit
  }
}

open class DefaultInlineCompletionInsertHandler : InlineCompletionInsertHandler {
  override fun afterInsertion(environment: InlineCompletionInsertEnvironment, elements: List<InlineCompletionElement>) {
    val skippedTextLength = elements.filterIsInstance<InlineCompletionSkipTextElement>().sumOf { it.text.length }
    val offset = environment.editor.caretModel.offset
    environment.editor.document.deleteString(offset, offset + skippedTextLength)
  }

  companion object {
    val INSTANCE = DefaultInlineCompletionInsertHandler()
  }
}
