package com.intellij.codeInsight.completion.inline

import com.intellij.codeInsight.completion.inline.InlineContext.Companion.getInlineContextOrNull
import com.intellij.codeInsight.completion.inline.InlineContext.Companion.removeInlineContext
import com.intellij.codeInsight.completion.inline.InlineContext.Companion.resetInlineContext
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Internal
class InlineCaretListener : CaretListener {
  override fun caretPositionChanged(event: CaretEvent) {
    event.editor.resetInlineContext()
  }
}

@ApiStatus.Internal
class InlineFocusListener : FocusChangeListener {
  override fun focusGained(editor: Editor) = Unit
  override fun focusLost(editor: Editor) {
    editor.resetInlineContext()
  }
}

@ApiStatus.Internal
class InlineKeyListener(private val editor: Editor) : KeyAdapter() {
  private val usedKeys = listOf(
    KeyEvent.VK_ALT,
    KeyEvent.VK_OPEN_BRACKET,
    KeyEvent.VK_CLOSE_BRACKET,
    KeyEvent.VK_TAB,
  )

  override fun keyReleased(event: KeyEvent) {
    if (usedKeys.contains(event.keyCode)) {
      return
    }
    editor.resetInlineContext()
  }
}

@ApiStatus.Internal
class AcceptInlineCompletionAction : EditorAction(AcceptInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      editor.getInlineContextOrNull()?.insert()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return editor.getInlineContextOrNull()?.startOffset == caret.offset
    }
  }
}

@ApiStatus.Internal
class EscapeHandler : EditorActionHandler() {
  public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    if (isEnabled(editor, caret, dataContext)) {
      execute(editor, caret, dataContext)
    }

    editor.removeInlineContext()
    editor.getInlineContextOrNull()?.let(Disposer::dispose)
  }

  public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    return editor.getInlineContextOrNull() != null
  }
}
