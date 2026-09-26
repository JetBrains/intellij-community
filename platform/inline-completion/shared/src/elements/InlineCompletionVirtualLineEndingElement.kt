// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

/**
 * When we use 'first line mode' and show only the first line of multiline, we'd like to show the syntactically correct code.
 * If we open a bracket and do not close it on the same line, we'd like to show that it's going to be closed further.
 *
 * E.g., for `{` it's shown as `...}`.
 */
@ApiStatus.Internal
class InlineCompletionVirtualLineEndingElement(
  val ending: String,
  val attributes: TextAttributes
) : InlineCompletionElement {
  override val text: String
    get() = ""

  override fun toPresentable(): InlineCompletionElement.Presentable {
    return Presentable(this)
  }

  class Presentable(override val element: InlineCompletionVirtualLineEndingElement) : InlineCompletionElement.Presentable {

    private var delegate: InlineCompletionElement.Presentable? = null

    override fun startOffset(): Int? = delegate?.startOffset()

    override fun endOffset(): Int? = delegate?.endOffset()

    override fun getBounds(): Rectangle? = null

    override fun isVisible(): Boolean = delegate?.isVisible() == true

    override fun render(editor: Editor, offset: Int) {
      delegate = InlineCompletionTextElement("..." + element.ending, element.attributes).toPresentable()
      delegate?.render(editor, offset)
    }

    override fun dispose() {
      delegate?.let { Disposer.dispose(it) }
      delegate = null
    }
  }
}
