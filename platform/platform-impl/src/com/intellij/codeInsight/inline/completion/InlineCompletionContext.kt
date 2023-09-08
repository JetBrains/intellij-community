// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineState.Companion.resetInlineCompletionState
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionKeyListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.render.InlineCompletion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionContext private constructor(
  val editor: Editor,
  private val invocationTime: Long,
  private var hasPlaceholder: Boolean = false
) : Disposable {
  private val keyListener = InlineCompletionKeyListener(editor)
  private val isSelecting = AtomicBoolean(false)
  private val inlay = InlineCompletion.forEditor(editor)
  private val showTracker = InlineCompletionUsageTracker.ShowTracker()

  private var completions = emptyList<InlineCompletionElement>()
  private var selectedIndex = -1

  val isCurrentlyDisplayingInlays: Boolean
    get() = !inlay.isEmpty && !hasPlaceholder

  val startOffset: Int?
    get() = inlay.offset

  fun update(proposals: List<InlineCompletionElement>, selectedIndex: Int, offset: Int) {
    LOG.trace("Update inline completion context")
    this.completions = proposals
    this.selectedIndex = selectedIndex
    val proposal = proposals[selectedIndex]
    val text = proposal.text

    if (!inlay.isEmpty) {
      inlay.reset()
    }
    if (text.isNotBlank() && editor is EditorImpl) {
      inlay.render(proposal, offset)
      showTracker.shown(invocationTime, editor, proposal)
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
    showTracker.accepted()

    isSelecting.set(false)
    InlineCompletionHandler.unmute()
    editor.removeInlineCompletionContext()
    LookupManager.getActiveLookup(editor)?.hideLookup(false)
    Disposer.dispose(this)
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_CONTEXT = Key.create<InlineCompletionContext>("inline.completion.context")

    fun Editor.initOrGetInlineCompletionContext(invocationTime: Long): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(this, invocationTime).also {
        LOG.trace("Create new inline completion context")
        putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    fun Editor.initOrGetInlineCompletionContextWithPlaceholder(invocationTime: Long): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(this, invocationTime, hasPlaceholder = true).also {
        LOG.trace("Create new inline completion context with placeholder")
        putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    fun Editor.resetInlineCompletionContextWithPlaceholder(): Unit? = getInlineCompletionContextOrNull()?.let {
      removeInlineCompletionContext()
      Disposer.dispose(it)
    }

    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getUserData(INLINE_COMPLETION_CONTEXT)
    fun Editor.removeInlineCompletionContext(): Unit = putUserData(INLINE_COMPLETION_CONTEXT, null).also {
      LOG.trace("Remove inline completion context")
    }

    fun Editor.resetInlineCompletionContext(): Unit? = getInlineCompletionContextOrNull()?.let {
      if (it.isCurrentlyDisplayingInlays) {
        it.showTracker.rejected()
        removeInlineCompletionContext()
        Disposer.dispose(it)
      }
    }
  }
}
