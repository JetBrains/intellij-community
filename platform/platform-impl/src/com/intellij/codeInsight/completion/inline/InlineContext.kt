package com.intellij.codeInsight.completion.inline

import com.intellij.codeInsight.completion.inline.InlineState.Companion.resetInlineState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class InlineContext private constructor(val editor: Editor) : Disposable {
  private val keyListener = InlineKeyListener(editor)
  private val isSelecting = AtomicBoolean(false)
  private val inlay = InlayCompletion.forEditor(editor)

  private var completions = emptyList<InlineCompletionProposal>()
  private var selectedIndex = -1

  init {
    editor.caretModel.addCaretListener(InlineCaretListener())
    if (editor is EditorEx) {
      editor.addFocusListener(InlineFocusListener())
    }
  }

  val isCurrentlyDisplayingInlays: Boolean
    get() = !inlay.isEmpty

  val startOffset: Int?
    get() = inlay.offset

  fun update(proposals: List<InlineCompletionProposal>, selectedIndex: Int, offset: Int) {
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
    editor.resetInlineState()
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
    editor.removeInlineContext()
    Disposer.dispose(this)
  }

  companion object {
    private val INLINE_CONTEXT = Key.create<InlineContext>("inline.completion.context")

    fun Editor.initOrGetInlineContext(): InlineContext = getUserData(INLINE_CONTEXT) ?: InlineContext(this).also {
      putUserData(INLINE_CONTEXT, it)
    }

    fun Editor.getInlineContextOrNull(): InlineContext? = getUserData(INLINE_CONTEXT)
    fun Editor.removeInlineContext() = putUserData(INLINE_CONTEXT, null)
    fun Editor.resetInlineContext() = getInlineContextOrNull()?.let {
      if (it.isCurrentlyDisplayingInlays) {
        removeInlineContext()
        Disposer.dispose(it)
      }
    }

  }
}
