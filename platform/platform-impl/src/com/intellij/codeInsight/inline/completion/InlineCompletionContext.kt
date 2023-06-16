// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineState.Companion.resetInlineCompletionState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionContext private constructor(val editor: Editor) : Disposable {
  private val keyListener = InlineCompletionKeyListener(editor)
  private val isSelecting = AtomicBoolean(false)
  private val inlay = InlineCompletion.forEditor(editor)

  private var completions = emptyList<InlineCompletionElement>()
  private var selectedIndex = -1

  init {
    editor.caretModel.addCaretListener(InlineCompletionCaretListener())
    if (editor is EditorEx) {
      editor.addFocusListener(InlineCompletionFocusListener())
    }
  }

  val isCurrentlyDisplayingInlays: Boolean
    get() = !inlay.isEmpty

  val startOffset: Int?
    get() = inlay.offset

  fun update(proposals: List<InlineCompletionElement>, selectedIndex: Int, offset: Int) {
    this.completions = proposals
    this.selectedIndex = selectedIndex
    val proposal = proposals[selectedIndex]
    val text = proposal.text

    if (!inlay.isEmpty) {
      inlay.reset()
    }
    if (!text.isEmpty() && editor is EditorImpl) {
      inlay.render(proposal, offset)
      if (!inlay.isEmpty) {
        Disposer.register(this, inlay)
        editor.contentComponent.addKeyListener(keyListener)
      }
    }
  }

  override fun dispose() {
    if (isSelecting.get()) {
      return
    }
    if (!inlay.isEmpty) {
      Disposer.dispose(inlay)
    }

    editor.contentComponent.removeKeyListener(keyListener)
    editor.resetInlineCompletionState()
  }

  fun insert() {
    isSelecting.set(true)
    InlineCompletionHandler.mute()
    val offset = inlay.offset ?: return
    if (selectedIndex < 0) return

    val currentCompletion = completions[selectedIndex]
    editor.document.insertString(offset, currentCompletion.text)
    editor.caretModel.moveToOffset(offset + currentCompletion.text.length)

    isSelecting.set(false)
    InlineCompletionHandler.unmute()
    editor.removeInlineCompletionContext()
    Disposer.dispose(this)
  }

  companion object {
    private val INLINE_COMPLETION_CONTEXT = Key.create<InlineCompletionContext>("inline.completion.completion.context")

    fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(this).also {
        putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }
    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getUserData(INLINE_COMPLETION_CONTEXT)
    fun Editor.removeInlineCompletionContext(): Unit = putUserData(INLINE_COMPLETION_CONTEXT, null)
    fun Editor.resetInlineCompletionContext(): Unit? = getInlineCompletionContextOrNull()?.let {
      if (it.isCurrentlyDisplayingInlays) {
        removeInlineCompletionContext()
        Disposer.dispose(it)
      }
    }

  }
}
