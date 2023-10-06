// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.SimpleTypingEvent
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.isCurrent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

class InlineCompletionDocumentListener(private val editor: Editor) : BulkAwareDocumentListener {
  override fun documentChangedNonBulk(event: DocumentEvent) {
    val handler = InlineCompletion.getHandlerOrNull(editor)

    if (!(ClientEditorManager.getClientId(editor) ?: ClientId.localId).isCurrent()) {
      hideInlineCompletion(editor, handler)
      return
    }

    handler?.invoke(InlineCompletionEvent.DocumentChange(event, editor))
  }

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }
}

open class InlineCompletionKeyListener(private val editor: Editor) : KeyAdapter() {

  override fun keyReleased(event: KeyEvent) {
    if (!isValuableKeyReleased(event)) {
      return
    }
    LOG.trace("Valuable key released event $event")
    hideInlineCompletion()
  }

  private fun isValuableKeyReleased(event: KeyEvent): Boolean {
    if (event.keyCode in MINOR_KEYS) {
      return false
    }
    if (LookupManager.getActiveLookup(editor) != null && event.keyCode in MINOR_WHEN_LOOKUP_KEYS) {
      return false
    }
    return true
  }

  protected open fun hideInlineCompletion() = hideInlineCompletion(editor)

  companion object {
    private val LOG = thisLogger()
    private val MINOR_KEYS = listOf(
      KeyEvent.VK_ALT,
      KeyEvent.VK_OPEN_BRACKET,
      KeyEvent.VK_CLOSE_BRACKET,
      KeyEvent.VK_TAB,
      KeyEvent.VK_SHIFT,
    )
    private val MINOR_WHEN_LOOKUP_KEYS = listOf(
      KeyEvent.VK_UP,
      KeyEvent.VK_DOWN,
    )
  }
}

// ML-1086
class InlineEditorMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    LOG.trace("Valuable mouse pressed event $event")
    hideInlineCompletion(event.editor)
  }

  companion object {
    private val LOG = thisLogger()
  }
}

class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusLost(editor: Editor, event: FocusEvent) {
    LOG.trace("Losing focus with ${event}, ${event.cause}")
    hideInlineCompletion(editor)
  }

  companion object {
    private val LOG = thisLogger()
  }
}

/**
 * Cancels inline completion (via [cancel]) as soon as one of the following happens:
 * * A caret added/removed.
 * * A new caret offset doesn't correspond to [expectedOffset].
 */
class InlineSessionWiseCaretListener(
  @RequiresEdt private val expectedOffset: () -> Int,
  @RequiresEdt private val cancel: () -> Unit
) : CaretListener {

  override fun caretAdded(event: CaretEvent) = cancel()

  override fun caretRemoved(event: CaretEvent) = cancel()

  override fun caretPositionChanged(event: CaretEvent) {
    if (event.oldPosition == event.newPosition) {
      // ML-1341
      // It means that we moved caret from the state 'before inline completion' to `after inline completion`
      // In such a case, the actual caret position does not change
      return cancel()
    }
    val newOffset = event.editor.logicalPositionToOffset(event.newPosition)
    if (newOffset != expectedOffset()) {
      return cancel()
    }
  }
}

class InlineCompletionTypedHandlerDelegate : TypedHandlerDelegate() {

  override fun beforeClosingParenInserted(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    allowDocumentChange(editor, c.toString())
    return super.beforeClosingParenInserted(c, project, editor, file)
  }

  override fun beforeClosingQuoteInserted(quote: CharSequence, project: Project, editor: Editor, file: PsiFile): Result {
    allowDocumentChange(editor, quote.toString())
    return super.beforeClosingQuoteInserted(quote, project, editor, file)
  }

  private fun allowDocumentChange(editor: Editor, typed: String) {
    val handler = InlineCompletion.getHandlerOrNull(editor)
    handler?.allowDocumentChange(SimpleTypingEvent(typed, false))
  }
}

class InlineCompletionAnActionListener : AnActionListener {
  override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
    handler.allowDocumentChange(SimpleTypingEvent(c.toString(), true))
  }
}

private fun hideInlineCompletion(editor: Editor) {
  val context = InlineCompletionContext.getOrNull(editor) ?: return
  InlineCompletion.getHandlerOrNull(editor)?.hide(false, context, true)
}

private fun hideInlineCompletion(editor: Editor, handler: InlineCompletionHandler?) {
  if (handler == null) return
  InlineCompletionContext.getOrNull(editor)?.let { handler.hide(false, it) }
}
