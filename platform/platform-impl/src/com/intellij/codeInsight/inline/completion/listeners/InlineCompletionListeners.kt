// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
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
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiFile
import com.intellij.util.application
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
    handler?.onDocumentEvent(event, editor)
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
    if (InlineCompletionContext.getOrNull(event.editor)?.containsPoint(event) == true) {
      return
    }
    LOG.trace("Valuable mouse pressed event $event")
    hideInlineCompletion(event.editor, FinishType.MOUSE_PRESSED)
  }

  private fun InlineCompletionContext.containsPoint(event: EditorMouseEvent): Boolean {
    val point = event.mouseEvent.point
    return state.elements.any { it.getBounds()?.contains(point) == true }
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
 * The listener has two mods:
 * * **Movement is prohibited**: cancels inline completion (via [cancel]) as soon as a caret moves to unexpected position
 * (defined by [completionOffset])
 * * **Adaptive**: [completionOffset] is updated each time a caret offset is changed.
 *
 * In any mode, the inline completion will be canceled if any of the following happens:
 * * Any caret is added/removed
 * * A caret leans to the right, as only left lean is permitted. Otherwise, inlays will be to the left of the caret.
 */
internal abstract class InlineSessionWiseCaretListener : CaretListener {

  protected abstract var completionOffset: Int
    @RequiresEdt
    get
    @RequiresEdt
    set

  protected abstract val mode: Mode
    @RequiresEdt
    get

  @RequiresEdt
  protected abstract fun cancel()

  override fun caretAdded(event: CaretEvent) = cancel()

  override fun caretRemoved(event: CaretEvent) = cancel()

  override fun caretPositionChanged(event: CaretEvent) {
    val newOffset = event.editor.logicalPositionToOffset(event.newPosition)
    when (mode) {
      Mode.ADAPTIVE -> {
        completionOffset = newOffset
      }
      Mode.PROHIBIT_MOVEMENT -> {
        if (event.oldPosition == event.newPosition) {
          // ML-1341
          // It means that we moved caret from the state 'before inline completion' to `after inline completion`
          // In such a case, the actual caret position does not change, only 'leansForward'
          cancel()
        }
        else if (newOffset != completionOffset) {
          cancel()
        }
      }
    }
  }

  protected enum class Mode {
    ADAPTIVE,
    PROHIBIT_MOVEMENT
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
      state.addTemplateStateListener(TemplateStateListener(editor, ::finish))
    }
  }

  private fun start(editor: Editor) {
    editor.putUserData(TEMPLATE_IN_PROGRESS_KEY, Unit)
  }

  private fun finish(editor: Editor) {
    editor.removeUserData(TEMPLATE_IN_PROGRESS_KEY)
  }

  private class TemplateStateListener(private val editor: Editor, private val finish: (Editor) -> Unit) : TemplateEditingListener {

    override fun templateFinished(template: Template, brokenOff: Boolean) {
      finish(editor)
      if (!brokenOff && template.isSuitableToInvokeEvent()) {
        application.invokeLater {
          if (!editor.isDisposed) {
            InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(InlineCompletionEvent.TemplateInserted(editor))
          }
        }
      }
    }

    override fun templateCancelled(template: Template?) {
      finish(editor)
    }

    override fun currentVariableChanged(templateState: TemplateState, template: Template?, oldIndex: Int, newIndex: Int) = Unit

    override fun waitingForInput(template: Template?) = Unit

    override fun beforeTemplateFinished(state: TemplateState, template: Template?) = Unit

    private fun Template.isSuitableToInvokeEvent(): Boolean {
      // Usually, 'key' is responsible for the name of the live template
      return key != ""
    }
  }

  companion object {
    val TEMPLATE_IN_PROGRESS_KEY = Key.create<Unit>("inline.completion.template.in.progress")
  }
}

private fun hideInlineCompletion(editor: Editor, finishType: FinishType) {
  val context = InlineCompletionContext.getOrNull(editor) ?: return
  InlineCompletion.getHandlerOrNull(editor)?.hide(context, finishType)
}
