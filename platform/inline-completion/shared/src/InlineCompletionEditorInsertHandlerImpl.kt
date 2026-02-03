// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock

internal class InlineCompletionEditorInsertHandlerImpl : InlineCompletionEditorInsertHandler {
  @RequiresWriteLock
  override fun insert(editor: Editor, textToInsert: String, offset: Int, file: PsiFile) {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    editor.document.insertString(offset, textToInsert)
    editor.caretModel.moveToOffset(textToInsert.length + offset)
    PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
  }

  override fun isApplicable(editor: Editor): Boolean = true
}
