// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineCompletionGrayTextElementRenderer
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

class InlineCompletionGrayTextElement(override val text: String) : InlineCompletionElement {

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  open class Presentable(override val element: InlineCompletionElement) : InlineCompletionElement.Presentable {
    private var offset: Int? = null
    private var rectangle: Rectangle? = null

    override fun isVisible(): Boolean = offset != null
    override fun startOffset(): Int? = offset
    override fun endOffset(): Int? = offset

    /**
     * Temporal workaround for an internal plugin. **Should not be used.**
     */
    @ApiStatus.Internal
    @ApiStatus.Experimental
    protected open fun getText(): String = element.text

    // TODO It returns the rectangle for all the elements, but now it's not important
    override fun getBounds(): Rectangle? = rectangle

    override fun render(editor: Editor, offset: Int) {
      rectangle = InlineCompletionGrayTextElementRenderer.render(editor, getText(), offset, this)
      this.offset = offset
    }

    override fun dispose() {
      offset = null
    }
  }
}
