// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineCompletionColorTextRenderManager
import com.intellij.codeInsight.inline.completion.render.RenderedInlineCompletionElementDescriptor
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Rectangle

// TODO docs
open class InlineCompletionColorTextElement @ApiStatus.Experimental constructor(
  override val text: String,
  val getColor: (Editor) -> Color
) : InlineCompletionElement {

  constructor(text: String, color: Color) : this(text, { color })

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this, getColor)

  open class Presentable(
    override val element: InlineCompletionElement,
    val getColor: (Editor) -> Color
  ) : InlineCompletionElement.Presentable {

    private var descriptor: RenderedInlineCompletionElementDescriptor? = null

    override fun isVisible(): Boolean = descriptor != null
    override fun startOffset(): Int? = descriptor?.getStartOffset()
    override fun endOffset(): Int? = descriptor?.getEndOffset()

    // TODO It returns the rectangle for all the elements, but now it's not important
    override fun getBounds(): Rectangle? = descriptor?.getRectangle()

    override fun render(editor: Editor, offset: Int) {
      descriptor = InlineCompletionColorTextRenderManager.render(editor, getText(), getColor(editor), offset, this)
    }

    override fun dispose() {
      descriptor = null
    }

    /**
     * Temporal workaround for an internal plugin. **Should not be used.**
     */
    @ApiStatus.Internal
    @ApiStatus.Experimental
    protected open fun getText(): String = element.text
  }
}
