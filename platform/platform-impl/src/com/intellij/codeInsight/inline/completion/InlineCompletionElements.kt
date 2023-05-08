// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class InlineCompletionRequest(val file: PsiFile, val event: DocumentEvent, val editor: Editor) {
  val document: Document
    get() = event.document
  val startOffset: Int
    get() = event.offset
  val endOffset: Int
    get() = event.offset + event.newLength

  companion object {
    fun fromDocumentEvent(event: DocumentEvent, editor: Editor): InlineCompletionRequest? {
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      val file = ReadAction.compute<PsiFile, Throwable> { PsiManager.getInstance(project).findFile(virtualFile) }

      return InlineCompletionRequest(file, event, editor)
    }
  }
}

@ApiStatus.Experimental
data class InlineCompletionElement(val text: String)
