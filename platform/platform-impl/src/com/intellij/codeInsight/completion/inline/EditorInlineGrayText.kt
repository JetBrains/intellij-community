package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Internal
class EditorInlineGrayText(private val editor: Editor) : GrayText {
  private var suffixInlay: Inlay<*>? = null
  private var blockInlay: Inlay<*>? = null

  override val offset = suffixInlay?.offset

  override val isEmpty = suffixInlay == null && blockInlay == null

  override fun getBounds(): Rectangle? {
    val bounds = blockInlay?.bounds?.let { Rectangle(it) }
    suffixInlay?.bounds?.let { bounds?.add(Rectangle(it)) }
    return bounds
  }

  override fun render(proposal: GrayTextElement, offset: Int) {
    if (proposal.text.isEmpty()) return
    val lines = proposal.text.lines()
    renderSuffix(editor, lines.first(), offset)
    if (lines.size > 1) {
      renderBlock(lines.stream().skip(1).toList(), editor, offset)
    }
  }


  override fun reset() {
    blockInlay?.let {
      Disposer.dispose(it)
      blockInlay = null
    }

    suffixInlay?.let {
      Disposer.dispose(it)
      suffixInlay = null
    }
  }

  override fun dispose() {
    blockInlay?.let { Disposer.dispose(it) }
    suffixInlay?.let { Disposer.dispose(it) }
    reset()
  }

  private fun renderSuffix(editor: Editor, line: String, offset: Int) {
    val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, line)) ?: return
    Disposer.tryRegister(this, element)
    suffixInlay = element
  }

  private fun renderBlock(
    lines: List<String>,
    editor: Editor,
    offset: Int
  ) {
    val element = editor.inlayModel.addBlockElement(
      offset, true, false, 1,
      InlineBlockElementRenderer(editor, lines)
    ) ?: return

    Disposer.tryRegister(this, element)
    blockInlay = element
  }
}
