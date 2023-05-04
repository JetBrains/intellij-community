package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Internal
interface GrayText : Disposable {
  val offset: Int?
  val isEmpty: Boolean

  fun render(proposal: GrayTextElement, offset: Int)
  fun getBounds(): Rectangle?
  fun reset()

  companion object {
    fun forEditor(editor: Editor): GrayText {
      return EditorInlineGrayText(editor)
    }
  }
}
