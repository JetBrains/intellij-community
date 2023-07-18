// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

private typealias EditorCaret = Caret

@ApiStatus.Experimental
data class InlineCompletionRequest(
  val event: InlineCompletionEvent,

  val file: PsiFile,
  val editor: Editor,
  val document: Document,
  val startOffset: Int,
  val endOffset: Int,
  val lookupElement: LookupElement? = null,
)

@ApiStatus.Experimental
data class InlineCompletionElement(val text: String) {
  fun withText(text: String) = InlineCompletionElement(text)
}

@ApiStatus.Experimental
sealed interface InlineCompletionEvent {
  fun toRequest(): InlineCompletionRequest?

  /**
   * A class representing a direct call in the code editor by [InsertInlineCompletionAction].
   */
  @ApiStatus.Experimental
  class DirectCall(val editor: Editor, val file: PsiFile, val caret: EditorCaret) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest {
      return InlineCompletionRequest(this, file, editor, editor.document, caret.offset, caret.offset)
    }
  }

  /**
   * Represents a non-dummy not empty document event call in the editor.
   */
  @ApiStatus.Experimental
  class Document(val event: DocumentEvent, val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val file = runReadAction { PsiManager.getInstance(project).findFile(virtualFile) } ?: return null

      return InlineCompletionRequest(this, file, editor, event.document, event.offset, event.offset + event.newLength)
    }
  }


  /**
   * Represents a caret move event only in selected editor.
   *
   * Be careful with this one because it might duplicate document events. It worth to add check for "non-simple offset change"
   * ```
   * if (event.oldPosition.line == event.newPosition.line && event.oldPosition.column + 1 == event.newPosition.column) {
   *   return
   * }
   * ```
   */
  @ApiStatus.Experimental
  @Deprecated("platform caret listener is disabled")
  class Caret(val event: EditorMouseEvent) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val editor = event.editor
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val (file, offset) = runReadAction {
        PsiManager.getInstance(project).findFile(virtualFile) to editor.caretModel.offset
      }
      if (file == null) return null

      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset)
    }
  }

  /**
   * A class representing a lookup event.
   *
   * This class implements the [InlineCompletionEvent] interface and provides a method to convert the event to a request
   * using the [toRequest] method.
   *
   * @param event The lookup event.
   */
  @ApiStatus.Experimental
  class Lookup(val event: LookupEvent) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val item = event.item ?: return null
      val editor = event.lookup?.editor ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null

      val (file, offset) = runReadAction {
        PsiManager.getInstance(project).findFile(virtualFile) to editor.caretModel.offset
      }
      if (file == null) return null

      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset, item)
    }
  }
}
