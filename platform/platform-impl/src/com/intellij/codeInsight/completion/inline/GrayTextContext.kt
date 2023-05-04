package com.intellij.codeInsight.completion.inline

import com.intellij.codeInsight.completion.inline.InlineState.Companion.resetGrayTextState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class GrayTextContext private constructor(val editor: Editor) : Disposable {
  private val keyListener = GrayTextKeyListener(editor)
  private val isSelecting = AtomicBoolean(false)
  private val inlay = GrayText.forEditor(editor)

  private var completions = emptyList<GrayTextElement>()
  private var selectedIndex = -1

  init {
    editor.caretModel.addCaretListener(GrayTextCaretListener())
    if (editor is EditorEx) {
      editor.addFocusListener(GrayTextFocusListener())
    }
  }

  val isCurrentlyDisplayingInlays: Boolean
    get() = !inlay.isEmpty

  val startOffset: Int?
    get() = inlay.offset

  fun update(proposals: List<GrayTextElement>, selectedIndex: Int, offset: Int) {
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
    editor.resetGrayTextState()
  }

  fun insert() {
    isSelecting.set(true)
    GrayTextHandler.mute()
    val offset = inlay.offset ?: return
    if (selectedIndex < 0) return

    val currentCompletion = completions[selectedIndex]
    editor.document.insertString(offset, currentCompletion.text)
    editor.caretModel.moveToOffset(offset + currentCompletion.text.length)

    isSelecting.set(false)
    GrayTextHandler.unmute()
    editor.removeGrayTextContext()
    Disposer.dispose(this)
  }

  companion object {
    private val GRAY_TEXT_CONTEXT = Key.create<GrayTextContext>("gray.text.completion.context")

    fun Editor.initOrGetGrayTextContext(): GrayTextContext = getUserData(GRAY_TEXT_CONTEXT) ?: GrayTextContext(this).also {
      putUserData(GRAY_TEXT_CONTEXT, it)
    }

    fun Editor.getGrayTextContextOrNull(): GrayTextContext? = getUserData(GRAY_TEXT_CONTEXT)
    fun Editor.removeGrayTextContext() = putUserData(GRAY_TEXT_CONTEXT, null)
    fun Editor.resetGrayTextContext() = getGrayTextContextOrNull()?.let {
      if (it.isCurrentlyDisplayingInlays) {
        removeGrayTextContext()
        Disposer.dispose(it)
      }
    }

  }
}
