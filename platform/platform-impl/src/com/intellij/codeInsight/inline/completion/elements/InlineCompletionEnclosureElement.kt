// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineCompletionInsertPolicy
import com.intellij.openapi.editor.Editor
import java.awt.Rectangle

class InlineCompletionEnclosureElement(val symbol: Char) : InlineCompletionElement {
  override val text: String = symbol.toString()
  override fun insertPolicy(): InlineCompletionInsertPolicy = InlineCompletionInsertPolicy.Skip(1)
  override fun withSameContent(): InlineCompletionElement = InlineCompletionEnclosureElement(symbol)
  override fun withTruncatedPrefix(length: Int): InlineCompletionElement? {
    return if (length > 0) null else withSameContent()
  }

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  class Presentable(val element: InlineCompletionEnclosureElement) : InlineCompletionElement.Presentable, InlineCompletionElement by element {
    private var startOffset: Int? = null
    private var endOffset: Int? = null
    private var isRendered = false

    override fun isVisible(): Boolean = isRendered

    override fun render(editor: Editor, offset: Int) {
      this.startOffset = offset
      this.endOffset = offset + 1
      isRendered = true
    }

    // TODO not that important, because a user likely hovers over gray text with their mouse
    override fun getBounds(): Rectangle? = null
    override fun startOffset(): Int? = startOffset
    override fun endOffset(): Int? = endOffset

    override fun dispose() {
      isRendered = false
      startOffset = null
      endOffset = null
    }
  }
}
