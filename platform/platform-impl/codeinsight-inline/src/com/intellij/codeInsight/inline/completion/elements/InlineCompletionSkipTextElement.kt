// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.openapi.editor.Editor
import java.awt.Rectangle

class InlineCompletionSkipTextElement(override val text: String) : InlineCompletionElement {

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  class Presentable(override val element: InlineCompletionSkipTextElement) : InlineCompletionElement.Presentable {
    private var startOffset: Int? = null
    private var endOffset: Int? = null
    private var isRendered = false

    override fun isVisible(): Boolean = isRendered

    override fun render(editor: Editor, offset: Int) {
      this.startOffset = offset
      this.endOffset = offset + element.text.length
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
