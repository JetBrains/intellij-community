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
class InlineCompletionShortcutAfterLineElement(
  lineNumber: Int,
  val isMultiline: Boolean,
  insertActionId: String
) : InlineCompletionShortcutHintElementBase(lineNumber, insertActionId) {

  override fun toPresentable(): InlineCompletionElement.Presentable {
    return Presentable(element = this, hint)
  }

  override fun getHintWeight(hint: InlineCompletionShortcutHint): Int {
    return when (hint) {
      InlineCompletionShortcutHint.INSERT -> 4
      InlineCompletionShortcutHint.INSERT_WORD -> 1
      InlineCompletionShortcutHint.INSERT_LINE -> if (isMultiline) 3 else 0
      InlineCompletionShortcutHint.INSERT_TERMINAL -> 0
    }
  }

  class Presentable(
    element: InlineCompletionShortcutHintElementBase,
    hint: InlineCompletionShortcutHint
  ) : InlineCompletionShortcutHintElementBase.Presentable(element, hint) {
    override fun renderShortcut(
      editor: EditorImpl,
      shortcut: String,
    ): List<Inlay<InlineCompletionShortcutHintRendererBase>> {
      val caretOffset = editor.caretModel.offset
      val shortcutRenderer = getShortcutRenderer(shortcut)
      val shortcutInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, shortcutRenderer)

      val suffixRenderer = getSuffixRenderer(editor)
      val suffixInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, suffixRenderer)

      return listOf(shortcutInlay, suffixInlay)
    }

    override fun additionalShouldRender(): Boolean {
      return InlineCompletionShortcutHintState.getState() == InlineCompletionShortcutHintState.SHOW_HINT
    }

    private fun getShortcutRenderer(currentShortcut: String): InlineCompletionShortcutHintRendererBase {
      return object : InlineCompletionShortcutHintRendererBase(currentShortcut) {

        // We need to check it here to be able to re-draw if a user changes the setting during a session
        override fun isEnabledAdditional(editor: Editor): Boolean = hintShouldRender()

        override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
          paintHint(inlay, g, r, textAttributes.clearEffects())
        }
      }
    }


    private fun getSuffixRenderer(editor: EditorImpl): InlineCompletionShortcutHintRendererBase {
      return object : InlineCompletionShortcutHintRendererBase(hint.suffixText) {

        // We need to check it here to be able to re-draw if a user changes the setting during a session
        override fun isEnabledAdditional(editor: Editor): Boolean = hintShouldRender()

        override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
          paintLabel(g, editor, r, text, textAttributes.clearEffects())
        }
      }
    }
  }
}