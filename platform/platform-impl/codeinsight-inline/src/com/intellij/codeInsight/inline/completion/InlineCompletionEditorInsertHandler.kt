// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Configures the insertion behavior of inline completions.
 * Only the first applicable handler is executed.
 * Setting a custom [InlineCompletionEditorInsertHandler] will not disable any other handlers like those executed after insert.
 * These expect the insertion to be immediately present in the document after executing this insert.
 */
@ApiStatus.Internal
interface InlineCompletionEditorInsertHandler {
  @RequiresEdt
  fun insert(editor: Editor, textToInsert: String, offset: Int, file: PsiFile)
  @RequiresEdt
  fun isApplicable(editor: Editor): Boolean

  companion object {
    val EP_NAME = ExtensionPointName.create<InlineCompletionEditorInsertHandler>("com.intellij.inline.completion.editorInsertHandler")
  }
}
