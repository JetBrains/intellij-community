// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Experimental
interface InlineCompletion : Disposable {
  val offset: Int?
  val isEmpty: Boolean

  fun render(proposal: InlineCompletionElement, offset: Int)
  fun getBounds(): Rectangle?
  fun reset()

  companion object {
    fun forEditor(editor: Editor): InlineCompletion {
      return EditorInlineInlineCompletion(editor)
    }
  }
}
