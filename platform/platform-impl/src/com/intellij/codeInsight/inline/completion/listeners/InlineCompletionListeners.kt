// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.inline.completion.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.isCurrent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Experimental
class InlineCompletionDocumentListener(private val editor: EditorImpl) : BulkAwareDocumentListener {
  override fun documentChangedNonBulk(event: DocumentEvent) {
    val handler = InlineCompletionHandler.getOrNull(editor)

    if (!(ClientEditorManager.getClientId(editor) ?: ClientId.localId).isCurrent()) {
      handler?.hide()
      return
    }

    if (!isEnabled(event) ) {
      // ML-1168
      handler?.hide()
    }

    // ML-1109 ML-1131 ML-1226
    if (event.newLength == 1 || event.newLength != 0 && event.newFragment.isBlank()) {
      LOG.trace("Valuable document event $event")
      handler?.invoke(InlineCompletionEvent.DocumentChange(event, editor))
    }
    else {
      handler?.hide()
    }
  }

  fun isEnabled(event: DocumentEvent): Boolean {
    return event.newFragment != CompletionUtil.DUMMY_IDENTIFIER && event.newLength >= 1
  }

  private fun InlineCompletionHandler.hide() {
    InlineCompletionContext.getOrNull(editor)?.let { hide(editor, false, it) }
  }

  companion object {
    private val LOG = thisLogger()
  }
}

@ApiStatus.Experimental
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

  protected open fun hideInlineCompletion() {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    InlineCompletionHandler.getOrNull(editor)?.hide(editor, false, context)
  }

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

// ML-1086 previously handled by [InlineCompletionFocusListener]
@ApiStatus.Experimental
class InlineEditorMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    LOG.trace("Valuable mouse pressed event $event")
    val context = InlineCompletionContext.getOrNull(event.editor) ?: return
    InlineCompletionHandler.getOrNull(event.editor)?.hide(event.editor, false, context)
  }

  companion object {
    private val LOG = thisLogger()
  }
}

@ApiStatus.Experimental
class InlineCaretListener : CaretListener {
  override fun caretPositionChanged(event: CaretEvent) {
    if (isSimple(event)) return

    LOG.trace("Valuable caret position event $event")
    val context = InlineCompletionContext.getOrNull(event.editor) ?: return
    InlineCompletionHandler.getOrNull(event.editor)?.hide(event.editor, false, context)
  }

  private fun isSimple(event: CaretEvent): Boolean {
    return event.oldPosition.line == event.newPosition.line && event.oldPosition.column + 1 == event.newPosition.column
  }

  companion object {
    private val LOG = thisLogger()
  }
}
