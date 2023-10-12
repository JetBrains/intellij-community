// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import java.awt.Rectangle

interface InlineCompletionElement {
  val text: String

  fun toPresentable(): Presentable

  interface Presentable : Disposable {
    val element: InlineCompletionElement

    fun isVisible(): Boolean
    fun render(editor: Editor, offset: Int)
    fun getBounds(): Rectangle?

    fun startOffset(): Int?
    fun endOffset(): Int?
  }
}
