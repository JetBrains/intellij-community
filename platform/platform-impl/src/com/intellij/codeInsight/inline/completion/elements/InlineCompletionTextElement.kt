// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineCompletionTextRenderManager
import com.intellij.codeInsight.inline.completion.render.RenderedInlineCompletionElementDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Experimental
open class InlineCompletionTextElement @ApiStatus.Experimental constructor(
  override val text: String,
  val getAttributes: (Editor) -> TextAttributes
) : InlineCompletionElement {

  constructor(text: String, attributes: TextAttributes) : this(text, { attributes })

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this, getAttributes)

  open class Presentable(
    override val element: InlineCompletionElement,
    val getAttributes: (Editor) -> TextAttributes
  ) : InlineCompletionElement.Presentable {

    private var descriptor: RenderedInlineCompletionElementDescriptor? = null

    override fun isVisible(): Boolean = descriptor != null
    override fun startOffset(): Int? = descriptor?.getStartOffset()
    override fun endOffset(): Int? = descriptor?.getEndOffset()

    // TODO It returns the rectangle for all the elements, but now it's not important
    override fun getBounds(): Rectangle? = descriptor?.getRectangle()

    override fun render(editor: Editor, offset: Int) {
      descriptor = InlineCompletionTextRenderManager.render(editor, getText(), getAttributes(editor), offset, this)
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
