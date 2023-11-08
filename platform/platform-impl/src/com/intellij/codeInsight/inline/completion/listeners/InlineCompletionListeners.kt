// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateState
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
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

internal class InlineCompletionDocumentListener(private val editor: Editor) : BulkAwareDocumentListener {
  override fun documentChangedNonBulk(event: DocumentEvent) {
    if (!(ClientEditorManager.getClientId(editor) ?: ClientId.localId).isCurrent()) {
      hideInlineCompletion(editor, FinishType.DOCUMENT_CHANGED)
      return
    }

    val handler = InlineCompletion.getHandlerOrNull(editor)
    if (handler != null) {
      if (event.isBlankAppended() && InlineCompletionNewLineTracker.isNewLineInsertion(editor)) {
        val range = TextRange.from(event.offset, event.newLength)
        handler.allowTyping(TypingEvent.NewLine(event.newFragment.toString(), range))
      }
      handler.onDocumentEvent(event, editor)
    }
  }

  private fun DocumentEvent.isBlankAppended(): Boolean {
    return oldLength == 0 && newLength > 0 && newFragment.isBlank()
  }

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }
}

@ApiStatus.Internal
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

  protected open fun hideInlineCompletion() = hideInlineCompletion(editor, FinishType.KEY_PRESSED)

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
internal class InlineEditorMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    LOG.trace("Valuable mouse pressed event $event")
    hideInlineCompletion(event.editor, FinishType.MOUSE_PRESSED)
  }

  companion object {
    private val LOG = thisLogger()
  }
}

internal class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusLost(editor: Editor, event: FocusEvent) {
    return // To not hide popup on tooltip change shortcut (and other provider buttons), click
    LOG.trace("Losing focus with ${event}, ${event.cause}")
    hideInlineCompletion(editor, FinishType.FOCUS_LOST)
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
internal class InlineSessionWiseCaretListener(
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

internal class InlineCompletionTypedHandlerDelegate : TypedHandlerDelegate() {

  override fun beforeClosingParenInserted(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    allowTyping(editor, c.toString())
    return super.beforeClosingParenInserted(c, project, editor, file)
  }

  override fun beforeClosingQuoteInserted(quote: CharSequence, project: Project, editor: Editor, file: PsiFile): Result {
    allowTyping(editor, quote.toString())
    return super.beforeClosingQuoteInserted(quote, project, editor, file)
  }

  private fun allowTyping(editor: Editor, typed: String) {
    val handler = InlineCompletion.getHandlerOrNull(editor)
    if (handler != null) {
      val offset = editor.caretModel.offset
      handler.allowTyping(TypingEvent.PairedEnclosureInsertion(typed, offset))
    }
  }
}

private class InlineCompletionTypingListener : AnActionListener {

  override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: return

    if (editor.getUserData(InlineCompletionTemplateListener.TEMPLATE_IN_PROGRESS_KEY) != null) {
      return // ML-1684 Do now show inline completion while refactoring
    }

    val offset = editor.caretModel.offset
    handler.allowTyping(TypingEvent.OneSymbol(c, offset))
  }
}

private class InlineCompletionTemplateListener : TemplateManagerListener {
  override fun templateStarted(state: TemplateState) {
    state.editor?.let { editor ->
      start(editor)
      state.addTemplateStateListener(TemplateStateListener { finish(editor) })
    }
  }

  private fun start(editor: Editor) {
    editor.putUserData(TEMPLATE_IN_PROGRESS_KEY, Unit)
  }

  private fun finish(editor: Editor) {
    editor.removeUserData(TEMPLATE_IN_PROGRESS_KEY)
  }

  private class TemplateStateListener(private val finish: () -> Unit) : TemplateEditingListener {
    override fun templateFinished(template: Template, brokenOff: Boolean) = finish()
    override fun templateCancelled(template: Template?) = finish()

    override fun currentVariableChanged(templateState: TemplateState, template: Template?, oldIndex: Int, newIndex: Int) = Unit
    override fun waitingForInput(template: Template?) = Unit
    override fun beforeTemplateFinished(state: TemplateState, template: Template?) = Unit
  }

  companion object {
    val TEMPLATE_IN_PROGRESS_KEY = Key.create<Unit>("inline.completion.template.in.progress")
  }
}

private fun hideInlineCompletion(editor: Editor, finishType: FinishType) {
  val context = InlineCompletionContext.getOrNull(editor) ?: return
  InlineCompletion.getHandlerOrNull(editor)?.hide(context, finishType)
}
