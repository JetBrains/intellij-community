// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
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
) : UserDataHolderBase()

@ApiStatus.Experimental
sealed interface InlineCompletionEvent {

  @RequiresBlockingContext
  fun toRequest(): InlineCompletionRequest?

  /**
   * A class representing a direct call in the code editor by [InsertInlineCompletionAction].
   */
  @ApiStatus.Experimental
  data class DirectCall(
    val editor: Editor,
    val file: PsiFile,
    val caret: EditorCaret,
    val context: DataContext? = null,
  ) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest {
      val offset = blockingReadAction { caret.offset }
      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset)
    }
  }

  /**
   * Represents a non-dummy not empty document event call in the editor.
   */
  @ApiStatus.Experimental
  data class DocumentChange(val event: DocumentEvent, val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val file = blockingReadAction { PsiManager.getInstance(project).findFile(virtualFile) } ?: return null

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
  data class CaretMove(val event: EditorMouseEvent) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val editor = event.editor
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val (file, offset) = blockingReadAction {
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
  data class LookupChange(val event: LookupEvent) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val item = event.item ?: return null
      val editor = runReadAction { event.lookup?.editor } ?: return null
      if (editor.caretModel.caretCount != 1) return null

      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null

      val (file, offset) = blockingReadAction {
        PsiManager.getInstance(project).findFile(virtualFile) to editor.caretModel.offset
      }
      if (file == null) return null

      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset, item)
    }
  }
}

@RequiresBlockingContext
private fun <T> blockingReadAction(block: () -> T): T {
  return application.runReadAction(Computable { block() })
}
