// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.codeInsight.inline.completion.MessageBundle
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.hint.InlineShortcutHintRendererBase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Rectangle
import kotlin.random.Random

@ApiStatus.Internal
sealed class InlineCompletionShortcutHintElementBase(val lineNumber: Int, private val insertActionId: String) : InlineCompletionElement {

  override val text: String
    get() = ""

  protected val hint: InlineCompletionShortcutHint by lazy { chooseHint() }

  protected abstract fun getHintWeight(hint: InlineCompletionShortcutHint): Int

  private fun chooseHint(): InlineCompletionShortcutHint {
    val entries = when (insertActionId) {
      IdeActions.ACTION_INSERT_INLINE_COMPLETION -> InlineCompletionShortcutHint.entries
      else -> return InlineCompletionShortcutHint.INSERT_TERMINAL
    }

    var randomPoint = Random.nextInt(entries.sumOf { getHintWeight(it) }) + 1
    return entries.firstOrNull {
      randomPoint -= getHintWeight(it)
      randomPoint <= 0
    } ?: entries.last()
  }

  abstract class Presentable(
    override val element: InlineCompletionShortcutHintElementBase,
    protected val hint: InlineCompletionShortcutHint
  ) : InlineCompletionElement.Presentable {
    private var inlays: List<Inlay<InlineCompletionShortcutHintRendererBase>> = emptyList()
    private var offset: Int? = null
    private var currentShortcut = getShortcutRepresentation()

    init {
      InlineCompletionShortcutChangeListener.whenInsertShortcutChanged(disposable = this) {
        rerender()
      }
    }

    protected abstract fun renderShortcut(
      editor: EditorImpl,
      shortcut: String,
    ): List<Inlay<InlineCompletionShortcutHintRendererBase>>

    protected abstract fun additionalShouldRender(): Boolean

    override fun isVisible(): Boolean {
      return getBounds() != null
    }

    override fun getBounds(): Rectangle? {
      if (!hintShouldRender()) {
        return null
      }
      var bounds: Rectangle? = null
      for (inlay in inlays) {
        val currentBounds = inlay.bounds
        if (currentBounds != null) {
          bounds = bounds?.union(currentBounds) ?: currentBounds
        }
      }
      return bounds
    }

    override fun startOffset(): Int? = offset

    override fun endOffset(): Int? = offset

    override fun dispose() {
      for (inlay in inlays) {
        Disposer.dispose(inlay)
      }
      inlays = emptyList()
      offset = null
    }

    override fun render(editor: Editor, offset: Int) {
      val currentShortcut = getShortcutRepresentation()
      if (editor !is EditorImpl || currentShortcut == null || !InlineShortcutHintRendererBase.isAvailableForLine(editor, element.lineNumber)) {
        return
      }
      try {
        inlays = renderShortcut(editor, currentShortcut)
        this.offset = offset
      }
      catch (e: Exception) {
        LOG.error("Could not render Full Line in-editor hint.", e)
      }
    }

    private fun rerender() {
      currentShortcut = getShortcutRepresentation()
      val editor = inlays.firstNotNullOfOrNull { it.editor as? EditorImpl }
      for (inlay in inlays) {
        Disposer.dispose(inlay)
      }
      inlays = emptyList()

      val shortcut = currentShortcut
      if (shortcut != null && editor != null) {
        inlays = renderShortcut(editor, shortcut)
      }
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

    protected fun hintShouldRender(): Boolean {
      return currentShortcut != null && additionalShouldRender()
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
  },
  INSERT_WORD {
    override val actionId: String
      get() = IdeActions.ACTION_INSERT_INLINE_COMPLETION_WORD
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.word.text")
  },
  INSERT_LINE {
    override val actionId: String
      get() = IdeActions.ACTION_INSERT_INLINE_COMPLETION_LINE
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.line.text")
  },
  INSERT_TERMINAL {
    override val actionId: String
      get() = ""
    override val suffixText: String
      get() = MessageBundle.message("inline.completion.shortcut.hint.insert.text")
  };

  abstract val actionId: String

  abstract val suffixText: @Nls String
}
