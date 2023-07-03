// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Experimental
class InlineCompletionDocumentListener(private val editor: EditorImpl) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    if (!isEnabled(event) || event.document.isInBulkUpdate) {
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
class InlineCompletionCaretListener : EditorMouseListener {
  override fun mouseClicked(event: EditorMouseEvent) {
    super.mouseClicked(event)

    // Check that edit is selected and opened
    if (!event.editor.isSelected()) {
      return
    }
    event.editor.resetInlineCompletionContext()
    event.editor.getUserData(InlineCompletionHandler.KEY)?.invoke(event)
  }

  private fun Editor.isSelected(): Boolean {
    val project = this.project ?: return false
    if (project.isDisposed()) {
      return false
    }
    val editorManager = FileEditorManager.getInstance(project) ?: return false

    if (editorManager is FileEditorManagerImpl) {
      val current = editorManager.getSelectedTextEditor(true)
      return current != null && current == this
    }

    val current = editorManager.getSelectedEditor()
    return current is TextEditor && this == current.getEditor()
  }
}

@ApiStatus.Experimental
class InlineCompletionKeyListener(private val editor: Editor) : KeyAdapter() {
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
    editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusLost(editor: Editor) {
    editor.resetInlineCompletionContext()
  }
}
