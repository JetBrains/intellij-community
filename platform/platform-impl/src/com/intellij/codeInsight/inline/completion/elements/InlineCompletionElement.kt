// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineCompletionInsertPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import java.awt.Rectangle

interface InlineCompletionElement {
  val text: String
  fun insertPolicy(): InlineCompletionInsertPolicy = InlineCompletionInsertPolicy.Append(text)

  /**
   * Returns a new [InlineCompletionElement] instance that has the same content as this one.
   * It should copy all the content, but should not copy any render information.
   */
  @Deprecated("will be removed")
  fun withSameContent(): InlineCompletionElement

  /**
   * Returns a new [InlineCompletionElement] instance with content identical to this [text], but with a shortened prefix of [length].
   * If there is no content after truncating, then `null` is returned.
   *
   * It is guaranteed that [text] length is at least [length].
   */
  @Deprecated("will be removed")
  fun withTruncatedPrefix(length: Int): InlineCompletionElement?

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
