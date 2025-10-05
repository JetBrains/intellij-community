// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Internal
class InlineCompletionShortcutBelowLineElement(lineNumber: Int, insertActionId: String) : InlineCompletionShortcutHintElementBase(lineNumber, insertActionId) {

  override fun toPresentable(): InlineCompletionElement.Presentable {
    return Presentable(this, hint)
  }

  override fun getHintWeight(hint: InlineCompletionShortcutHint): Int {
    return when (hint) {
      InlineCompletionShortcutHint.INSERT -> 1
      InlineCompletionShortcutHint.INSERT_WORD -> 2 // show it twice more often than 'insert' hint
      InlineCompletionShortcutHint.INSERT_LINE -> 0 // never show 'complete a line'
      InlineCompletionShortcutHint.INSERT_TERMINAL -> 0
    }
  }

  class Presentable(
    override val element: InlineCompletionShortcutBelowLineElement,
    hint: InlineCompletionShortcutHint,
  ) : InlineCompletionShortcutHintElementBase.Presentable(element, hint) {

    override fun renderShortcut(
      editor: EditorImpl,
      shortcut: String
    ): List<Inlay<InlineCompletionShortcutHintRendererBase>> {
      val caretOffset = editor.caretModel.offset
      val hintRenderer = getHintRenderer(shortcut)
      return listOf(
        editor.inlayModel.addBlockElement(caretOffset, true, false, 0, hintRenderer)
      )
    }

    override fun additionalShouldRender(): Boolean {
      return true
    }

    private fun getHintRenderer(currentShortcut: String): InlineCompletionShortcutHintRendererBase {
      val text = currentShortcut + " " + hint.suffixText
      return object : InlineCompletionShortcutHintRendererBase(text) {
        override fun isEnabledAdditional(editor: Editor): Boolean = hintShouldRender()

        override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
          paintHint(inlay, g, r, textAttributes.clearEffects())
        }
      }
    }
  }
}
