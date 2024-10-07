// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.ApiStatus
import kotlin.random.Random

class InlineCompletionRequest(
  val event: InlineCompletionEvent,

  val file: PsiFile,
  val editor: Editor,
  val document: Document,
  val startOffset: Int,
  val endOffset: Int,
  val lookupElement: LookupElement? = null,
) : UserDataHolderBase() {
  @ApiStatus.Internal
  val requestId: Long = Random.nextLong()
}

sealed interface TypingEvent {

  val typed: String

  val range: TextRange

  class OneSymbol internal constructor(symbol: Char, offset: Int) : TypingEvent {
    override val typed: String = symbol.toString()

    override val range: TextRange = TextRange(offset, offset + 1)
  }

  class NewLine @ApiStatus.Internal constructor(override val typed: String, override val range: TextRange) : TypingEvent

  class PairedEnclosureInsertion internal constructor(override val typed: String, offset: Int) : TypingEvent {
    override val range: TextRange = TextRange(offset, offset) // caret does not move
  }
}

/**
 * Be aware that creating your own event is unsafe for a while and might face compatibility issues
 */
@ApiStatus.NonExtendable
interface InlineCompletionEvent {

  fun toRequest(): InlineCompletionRequest?

  /**
   * A class representing a direct call in the code editor by [InsertInlineCompletionAction].
   */
  class DirectCall(
    val editor: Editor,
    val caret: Caret,
    val context: DataContext? = null,
  ) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val offset = runReadAction { caret.offset }
      val project = editor.project ?: return null
      val file = getPsiFile(caret, project) ?: return null
      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset)
    }
  }

  /**
   * Represents a typing in an editor.
   *
   * Since document changes are hard to correctly track, it's forbidden to create them outside this module.
   */
  class DocumentChange @ApiStatus.Internal constructor(val typing: TypingEvent, val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val project = editor.project ?: return null
      val caretModel = editor.caretModel
      if (caretModel.caretCount != 1) return null

      val file = getPsiFile(caretModel.currentCaret, project) ?: return null
      return InlineCompletionRequest(this, file, editor, editor.document, typing.range.startOffset, typing.range.endOffset)
    }
  }

  /**
   * Represents a backspace hit for removal of characters in an editor. Backspace is allowed if:
   * * There is no selection
   * * There is only one caret
   * * Only one character is removed
   *
   * More or fewer cases may be supported in the future.
   *
   * It is triggered after the backspace is processed.
   *
   * **Note**: for now, it's impossible to update a session with this event. Inline Completion will be hidden once a backspace is pressed.
   */
  @ApiStatus.Experimental
  class Backspace internal constructor(val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val project = editor.project ?: return null
      val caretModel = editor.caretModel
      if (caretModel.caretCount != 1) return null

      val file = getPsiFile(caretModel.currentCaret, project) ?: return null
      // TODO offset
      return InlineCompletionRequest(this, file, editor, editor.document, caretModel.offset, caretModel.offset)
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
  class LookupChange(override val event: LookupEvent) : InlineLookupEvent {
    override fun toRequest(): InlineCompletionRequest? {
      return super.toRequest()?.takeIf { it.lookupElement != null }
    }
  }

  /**
   * Represents an event when a lookup is cancelled during inline completion.
   *
   * @param event The lookup event associated with the cancellation.
   */
  class LookupCancelled(override val event: LookupEvent) : InlineLookupEvent

  sealed interface InlineLookupEvent : InlineCompletionEvent {
    val event: LookupEvent
    override fun toRequest(): InlineCompletionRequest? {
      val editor = runReadAction { event.lookup?.editor } ?: return null
      val caretModel = editor.caretModel
      if (caretModel.caretCount != 1) return null

      val project = editor.project ?: return null

      val (file, offset) = runReadAction {
        getPsiFile(caretModel.currentCaret, project) to caretModel.offset
      }
      if (file == null) return null

      return InlineCompletionRequest(this, file, editor, editor.document, offset, offset, event.item)
    }
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  sealed class PartialAccept @ApiStatus.Internal constructor(val editor: Editor) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
      val caretModel = editor.caretModel
      if (caretModel.caretCount != 1) return null
      val session = InlineCompletionSession.getOrNull(editor) ?: return null
      return InlineCompletionRequest(
        this,
        session.request.file,
        editor,
        editor.document,
        caretModel.offset,
        caretModel.offset, // It depends on specific insertion implementation, so no way to guess it here
        lookupElement = null
      )
    }
  }

  /**
   * **This event is not intended to be a start of the inline completion.**
   *
   * This event is to be called when some inline completion suggestion is already rendered.
   * It inserts the first word from the suggestion to the editor.
   */
  @ApiStatus.Experimental
  class InsertNextWord @ApiStatus.Internal constructor(editor: Editor) : PartialAccept(editor)

  /**
   * **This event is not intended to be a start of the inline completion.**
   *
   * This event is to be called when some inline completion suggestion is already rendered.
   * It inserts the first line from the suggestion to the editor.
   */
  @ApiStatus.Experimental
  class InsertNextLine @ApiStatus.Internal constructor(editor: Editor) : PartialAccept(editor)
}

private fun getPsiFile(caret: Caret, project: Project): PsiFile? {
  return runReadAction {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(caret.editor.document) ?: return@runReadAction null
    // * [PsiUtilBase] takes into account injected [PsiFile] (like in Jupyter Notebooks)
    // * However, it loads a file into the memory, which is expensive
    // * Some tests forbid loading a file when tearing down
    // * On tearing down, Lookup Cancellation happens, which causes the event
    // * Existence of [treeElement] guarantees that it's in the memory
    if (file.isLoadedInMemory()) {
      PsiUtilBase.getPsiFileInEditor(caret, project)
    }
    else {
      file
    }
  }
}

private fun PsiFile.isLoadedInMemory(): Boolean {
  return (this as? PsiFileImpl)?.treeElement != null
}
