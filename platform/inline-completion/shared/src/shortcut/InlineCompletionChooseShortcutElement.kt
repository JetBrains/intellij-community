// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.codeInsight.inline.completion.MessageBundle
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.tooltip.ChangeToCustomInlineCompletionAction
import com.intellij.codeInsight.inline.completion.tooltip.InplaceChangeInlineCompletionShortcutAction
import com.intellij.codeInsight.inline.hint.InlineShortcutHintRendererBase
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

@ApiStatus.Internal
class InlineCompletionChooseShortcutElement(val lineNumber: Int) : InlineCompletionElement {
  override val text: String = ""

  init {
    InlineCompletionShortcutHintListener.choiceShown()
  }

  override fun toPresentable(): InlineCompletionElement.Presentable {
    return Presentable(this)
  }

  class Presentable(override val element: InlineCompletionChooseShortcutElement) : InlineCompletionElement.Presentable {
    private var shortcutInlay: Inlay<ShortcutRenderer>? = null
    private var textInlay: Inlay<TextRenderer>? = null
    private var offset: Int? = null

    override fun isVisible(): Boolean {
      return getBounds() != null
    }

    override fun getBounds(): Rectangle? {
      if (!choiceShouldRender()) {
        return null
      }
      val shortcutBounds = shortcutInlay?.bounds ?: return null
      val suffixBounds = textInlay?.bounds ?: return null
      return shortcutBounds.union(suffixBounds)
    }

    override fun startOffset(): Int? = offset

    override fun endOffset(): Int? = offset

    override fun dispose() {
      shortcutInlay?.let(Disposer::dispose)
      textInlay?.let(Disposer::dispose)
      shortcutInlay = null
      textInlay = null
      offset = null
    }

    override fun render(editor: Editor, offset: Int) {
      InlineCompletionShortcutChangeListener.whenInsertShortcutChanged(disposable = this) {
        shortcutInlay?.update()
        textInlay?.update()
      }

      if (editor !is EditorImpl || !InlineShortcutHintRendererBase.isAvailableForLine(editor, element.lineNumber)) {
        return
      }
      try {
        val caretOffset = editor.caretModel.offset
        val hintState = HintState(false)
        val textBefore = MessageBundle.message("inline.completion.shortcut.choose.shortcut.hint.text")
        val textRenderer = TextRenderer(textBefore, hintState, element.lineNumber)
        val shortcutRenderer = ShortcutRenderer("Tab " + '\u25BE', hintState, element.lineNumber)
        textInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, textRenderer).apply {
          renderer.initialize(this)
        }
        shortcutInlay = editor.inlayModel.addAfterLineEndElement(caretOffset, true, shortcutRenderer).apply {
          renderer.initialize(this)
        }
        this.offset = offset
      }
      catch (e: Exception) {
        LOG.error("Could not render ML completion in-editor choice hint.", e)
      }
    }

    companion object {
      private val LOG = logger<InlineCompletionChooseShortcutElement>()
    }
  }
}

private class HintState(var isHovered: Boolean) {
  private val updates = mutableListOf<() -> Unit>()

  fun whenUpdated(block: () -> Unit) {
    updates += block
  }

  fun update() {
    updates.forEach { it() }
  }
}

private abstract class RendererBase(
  text: String?,
  val hintState: HintState,
  private val lineNumber: Int
) : InlineCompletionShortcutHintRendererBase(text), InputHandler {
  private lateinit var myInlay: Inlay<*>

  override fun getTextAttributes(editor: Editor): TextAttributes? {
    return super.getTextAttributes(editor)?.updateIfHovering()
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return if (!choiceShouldRender()) 1 else super.calcWidthInPixels(inlay)
  }

  fun initialize(inlay: Inlay<*>) {
    check(!::myInlay.isInitialized)
    myInlay = inlay
    inlay.whenDisposed {
      updateState(false)
    }
    hintState.whenUpdated {
      inlay.update()
    }
  }

  fun TextAttributes.updateIfHovering(): TextAttributes {
    return if (hintState.isHovered) {
      clone().apply {
        foregroundColor = JBUI.CurrentTheme.Link.Foreground.ENABLED
      }
    }
    else this
  }

  override fun isEnabledAdditional(editor: Editor): Boolean = choiceShouldRender()

  override fun mousePressed(event: MouseEvent, translated: Point) {
    if (choiceShouldRender()) {
      event.consume()
    }
  }

  override fun mouseReleased(event: MouseEvent, translated: Point) {
    if (choiceShouldRender()) {
      showChooseShortcutPopup(myInlay.editor, guessPopupPoint(event), lineNumber)
    }
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    if (choiceShouldRender()) {
      updateState(true)
    }
  }

  override fun mouseExited() {
    if (choiceShouldRender()) {
      updateState(false)
    }
  }

  private fun updateState(isHovered: Boolean) {
    hintState.isHovered = isHovered
    if (myInlay.isValid) {
      updateCursor(isHovered)
      hintState.update()
    }
  }

  private fun updateCursor(isHovered: Boolean) {
    val cursor = when (isHovered) {
      true -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      false -> Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    }
    val contentComponent = myInlay.editor.contentComponent
    if (contentComponent.cursor != cursor) {
      UIUtil.setCursor(contentComponent, cursor)
    }
  }

  private fun guessPopupPoint(event: MouseEvent): RelativePoint {
    val shortcutInlay = myInlay.editor.inlayModel.getAfterLineEndElementsForLogicalLine(lineNumber).firstOrNull {
      it.renderer is ShortcutRenderer
    }
    val point = shortcutInlay?.bounds?.let { Point(it.x, it.y) } ?: event.point
    return RelativePoint(event.component, Point(point.x, point.y + myInlay.editor.lineHeight))
  }
}

private class ShortcutRenderer(
  text: String?,
  hoveringState: HintState,
  lineNumber: Int
) : RendererBase(text, hoveringState, lineNumber), InputHandler {

  override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    paintHint(inlay, g, r, textAttributes.updateIfHovering().clearEffects())
  }
}

private class TextRenderer(
  text: String?,
  hoveringState: HintState,
  lineNumber: Int
) : RendererBase(text, hoveringState, lineNumber), InputHandler {

  override fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    paintLabel(g, inlay.editor as EditorImpl, r, text, textAttributes) { it.updateIfHovering() }
  }
}

private fun choiceShouldRender(): Boolean {
  return InlineCompletionShortcutHintState.getState() == InlineCompletionShortcutHintState.SHOW_CHOICE
}

private fun showChooseShortcutPopup(editor: Editor, relativePoint: RelativePoint, lineNumber: Int) {
  val predefinedShortcuts = listOf(
    "Tab" to KeyboardShortcut.fromString("TAB"),
    "→" to KeyboardShortcut.fromString("RIGHT"),
    "Enter" to KeyboardShortcut.fromString("ENTER"),
    "Shift →" to KeyboardShortcut.fromString("shift pressed RIGHT")
  ).map { (name, shortcut) ->
    ChangeShortcutAction(name, shortcut, editor, lineNumber)
  }
  val defaultGroup = DefaultActionGroup().apply {
    addAll(predefinedShortcuts)
    add(ChangeToCustomInlineCompletionAction())
  }
  val popup = JBPopupFactory.getInstance().createActionGroupPopup(
    IdeBundle.message("inline.completion.tooltip.shortcuts.header"),
    defaultGroup,
    DataContext.EMPTY_CONTEXT,
    true,
    null,
    10
  )
  popup.show(relativePoint)
}

private class ChangeShortcutAction(
  @NlsActions.ActionText text: String,
  shortcut: Shortcut,
  private val editor: Editor,
  private val lineNumber: Int
) : InplaceChangeInlineCompletionShortcutAction(text, shortcut) {

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    InlineCompletionShortcutHintListener.choiceMade()
    val inlaysAfterLine = editor.inlayModel.getAfterLineEndElementsForLogicalLine(lineNumber)
    val relatedInlays = inlaysAfterLine.filter { it.renderer is InlineCompletionShortcutHintRendererBase }
    relatedInlays.forEach { it.update() }
  }
}
