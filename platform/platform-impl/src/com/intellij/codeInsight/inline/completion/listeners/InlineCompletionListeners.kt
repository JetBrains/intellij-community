// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContextWithPlaceholder
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.isCurrent
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Experimental
class InlineCompletionDocumentListener(private val editor: EditorImpl) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    if (!isEnabled(event) || event.document.isInBulkUpdate || !(ClientEditorManager.getClientId(editor) ?: ClientId.localId).isCurrent()) {
      return
    }

    if (event.document.isInBulkUpdate) return
    editor.getUserData(InlineCompletionHandler.KEY)?.invoke(event, editor)
  }

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }
}

@ApiStatus.Experimental
open class InlineCompletionKeyListener(private val editor: Editor) : KeyAdapter() {
  private val usedKeys = listOf(
    KeyEvent.VK_ALT,
    KeyEvent.VK_OPEN_BRACKET,
    KeyEvent.VK_CLOSE_BRACKET,
    KeyEvent.VK_TAB,
  )

  override fun keyReleased(event: KeyEvent) {
    if (event.keyCode in usedKeys) {
      return
    }
    hideInlineCompletion()
  }

  protected open fun hideInlineCompletion() {
    editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusLost(editor: Editor) {
    editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InlineCaretListener(private val editor: Editor) : CaretListener {
  override fun caretPositionChanged(event: CaretEvent) {
    if (isSimple(event)) return

    editor.resetInlineCompletionContextWithPlaceholder()
  }

  private fun isSimple(event: CaretEvent): Boolean {
    return event.oldPosition.line == event.newPosition.line && event.oldPosition.column + 1 == event.newPosition.column
  }
}
