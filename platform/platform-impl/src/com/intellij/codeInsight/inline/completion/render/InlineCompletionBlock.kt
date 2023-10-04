// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Experimental
interface InlineCompletionBlock : Disposable {
  // TODO take out all render information into a separate entity to guarantee whether they are null at the same time
  val startOffset: Int?
  val endOffset: Int?
  val isEmpty: Boolean
  val text: String
  val insertPolicy: InlineCompletionInsertPolicy

  fun render(editor: Editor, offset: Int)
  fun getBounds(): Rectangle?

  /**
   * Returns a new [InlineCompletionBlock] instance that has the same content as this one.
   * It should copy all the content, but should not copy any render information.
   */
  fun withSameContent(): InlineCompletionBlock

  /**
   * Returns a new [InlineCompletionBlock] instance with content identical to this [text], but with a shortened prefix of [length].
   * If there is no content after truncating, then `null` is returned.
   *
   * It is guaranteed that [text] length is at least [length].
   */
  fun withTruncatedPrefix(length: Int): InlineCompletionBlock?
}

@ApiStatus.Experimental
sealed interface InlineCompletionInsertPolicy {

  val caretShift: Int

  data class Append(val text: String) : InlineCompletionInsertPolicy {
    override val caretShift: Int
      get() = text.length
  }

  data class Skip(val length: Int) : InlineCompletionInsertPolicy {
    override val caretShift: Int
      get() = length
  }
}
