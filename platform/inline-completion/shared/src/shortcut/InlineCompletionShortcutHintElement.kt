// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.codeInsight.inline.completion.MessageBundle
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.shortcut.InlineCompletionShortcutHint.entries
import com.intellij.codeInsight.inline.hint.InlineShortcutHintRendererBase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Rectangle
import kotlin.random.Random

@ApiStatus.Internal
class InlineCompletionShortcutHintElement(val lineNumber: Int, val isMultiline: Boolean) : InlineCompletionElement {
  override val text: String = ""

  private val hint = InlineCompletionShortcutHint.random(isMultiline)

  override fun toPresentable(): InlineCompletionElement.Presentable {
    return Presentable(this, hint)
  }

  class Presentable(
    override val element: InlineCompletionShortcutHintElement,
    private val hint: InlineCompletionShortcutHint
  ) : InlineCompletionElement.Presentable {
    private var shortcutInlay: Inlay<InlineCompletionShortcutHintRendererBase>? = null
    private var suffixInlay: Inlay<InlineCompletionShortcutHintRendererBase>? = null
    private var offset: Int? = null
    private var currentShortcut = getShortcutRepresentation()

    init {
      InlineCompletionShortcutChangeListener.whenInsertShortcutChanged(disposable = this) {
        rerender()
      }
    }

    override fun isVisible(): Boolean {
      return getBounds() != null
    }

    override fun getBounds(): Rectangle? {
      if (!hintShouldRender()) {
        return null
      }
      val shortcutBounds = shortcutInlay?.bounds ?: return null
      val suffixBounds = suffixInlay?.bounds ?: return null
      return shortcutBounds.union(suffixBounds)
    }

    override fun startOffset(): Int? = offset

    override fun endOffset(): Int? = offset

    override fun dispose() {
      shortcutInlay?.let(Disposer::dispose)
      suffixInlay?.let(Disposer::dispose)
      shortcutInlay = null
      suffixInlay = null
      offset = null
    }

    override fun render(editor: Editor, offset: Int) {
      if (editor !is EditorImpl || currentShortcut == null || !InlineShortcutHintRendererBase.isAvailableForLine(editor, element.lineNumber)) {
        return
      }
      try {
        val caretOffset = editor.caretModel.offset
        shortcutInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, getShortcutRenderer())
        suffixInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, getSuffixRenderer(editor))
        this.offset = offset
      }
      catch (e: Exception) {
        LOG.error("Could not render Full Line in-editor hint.", e)
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

    private fun getShortcutRenderer(): InlineCompletionShortcutHintRendererBase {
      return object : InlineCompletionShortcutHintRendererBase(currentShortcut) {

        // We need to check it here to be able to re-draw if a user changes the setting during a session
        override fun isEnabledAdditional(editor: Editor): Boolean = hintShouldRender()

        override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
          paintHint(inlay, g, r, textAttributes.clearEffects())
        }
      }
    }

    private fun rerender() {
      currentShortcut = getShortcutRepresentation()
      shortcutInlay?.renderer?.text = currentShortcut
      shortcutInlay?.update()
      suffixInlay?.update()
    }

    private fun getShortcutRepresentation(): String? {
      val shortcut = KeymapUtil.getPrimaryShortcut(hint.actionId)
      return shortcut?.asString()
    }

    private fun Shortcut.asString(): String {
      return when (toString()) {
        "[pressed TAB]" -> "Tab"
        "[pressed ENTER]" -> "Enter"
        "[shift pressed RIGHT]" -> "Shift →"
        else -> KeymapUtil.getShortcutText(this)
      }
    }

    private fun hintShouldRender(): Boolean {
      return currentShortcut != null && InlineCompletionShortcutHintState.getState() == InlineCompletionShortcutHintState.SHOW_HINT
    }

    companion object {
      private val LOG = thisLogger()
    }
  }
}

@ApiStatus.Internal
enum class InlineCompletionShortcutHint {
  INSERT {
    override val actionId: String
      get() = IdeActions.ACTION_INSERT_INLINE_COMPLETION
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.text")
    override val priority: Int
      get() = 4
  },
  INSERT_WORD {
    override val actionId: String
      get() = IdeActions.ACTION_INSERT_INLINE_COMPLETION_WORD
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.word.text")
    override val priority: Int
      get() = 1
  },
  INSERT_LINE {
    override val actionId: String
      get() = IdeActions.ACTION_INSERT_INLINE_COMPLETION_LINE
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.line.text")
    override val priority: Int
      get() = 3
  };

  abstract val actionId: String

  abstract val suffixText: @Nls String

  protected abstract val priority: Int

  companion object {
    fun random(isMultiline: Boolean): InlineCompletionShortcutHint {
      val entries = if (isMultiline) entries else entries.filter { it !== INSERT_LINE }
      var randomPoint = Random.nextInt(entries.sumOf { it.priority }) + 1
      return entries.firstOrNull {
        randomPoint -= it.priority
        randomPoint <= 0
      } ?: entries.last()
    }
  }
}
