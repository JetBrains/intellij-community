// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.impl.EditorHyperlinkUsageCollector.HyperlinkFollowedPlace
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HintHint
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.system.OS
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal class InvisibleHyperlinkHintManager(private val editor: Editor, parentDisposable: Disposable) {

  private var hintInfo: HintInfo? = null

  // As the editor is focused on mousePressed and popup is shown on mouseReleased,
  // we need to capture the editor focus state.
  private var wasEditorFocusedBeforePopupShown: Boolean = false

  init {
    editor.addEditorMouseListener(object: EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) {
        wasEditorFocusedBeforePopupShown = editor.contentComponent.isFocusOwner
      }
    }, parentDisposable)
  }

  fun isInsideHint(e: EditorMouseEvent): Boolean {
    val hint = getHintInfoIfVisible()?.hint
    return hint != null && hint.isInsideHint(RelativePoint(e.mouseEvent))
  }

  fun onHoveredLinkChange(hoveredLink: RangeHighlighter?, e: EditorMouseEvent) {
    val hintInfo = getHintInfoIfVisible()
    if (hintInfo != null && hintInfo.link !== hoveredLink && !isInsideHintOrBetweenHintAndLink(e)) {
      // hide the popup if the mouse is outside the link, the popup, and the area between them
      hideIfVisible() 
    }
  }

  private fun isInsideHintOrBetweenHintAndLink(e: EditorMouseEvent): Boolean {
    val hintInfo = getHintInfoIfVisible() ?: return false
    val hint = hintInfo.hint
    val mousePoint = RelativePoint(e.mouseEvent)
    if (hint.isInsideHint(mousePoint)) {
      return true
    }
    val hintBounds = Rectangle(hint.component.locationOnScreen, hint.component.size)
    val initialMouseY = RelativePoint(hintInfo.initialEvent.mouseEvent).screenPoint.y
    val neighbourhood = hintBounds.union(Rectangle(Point(hintBounds.location.x, initialMouseY)))
    return neighbourhood.height < hintBounds.height * 4 /* sanity check */ &&
           neighbourhood.contains(mousePoint.screenPoint)
  }

  fun showHint(link: RangeHighlighterEx, e: EditorMouseEvent, action: () -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    hideIfVisible()
    var linkFollowed = false
    val component = createHintLabel(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        linkFollowed = true
        action()
        hideIfVisible()
        EditorHyperlinkUsageCollector.logInvisibleHyperlinkFollowed(HyperlinkFollowedPlace.POPUP_LINK_CLICKED)
      }
    })
    val hint = showHint(editor, e.offset, component)
    hintInfo = HintInfo(hint, link, e)
    val copyWasEditorFocusedBeforePopupShown = wasEditorFocusedBeforePopupShown
    hint.addHintListener {
      if (hintInfo?.hint == hint) {
        hintInfo = null
        EditorHyperlinkUsageCollector.logInvisibleHyperlinkPopupHidden(copyWasEditorFocusedBeforePopupShown, linkFollowed)
      }
    }
    EditorHyperlinkUsageCollector.logInvisibleHyperlinkPopupShown(wasEditorFocusedBeforePopupShown)
  }

  private fun createHintLabel(listener: HyperlinkListener): JComponent {
    val hintHint = HintUtil.getInformationHint()
    hintHint.setTextFg(JBUI.CurrentTheme.Tooltip.shortcutForeground())
    val hintLabel = HintUtil.createLabel(getHtmlText(hintHint), null, hintHint.textBackground, hintHint)
    hintLabel.pane!!.addHyperlinkListener(listener)
    return hintLabel
  }

  private fun getHtmlText(hintHint: HintHint): @Nls String {
    val htmlBodyText = HtmlBuilder()
      .append(HtmlChunk.link("", IdeBundle.message("editor.invisible.link.popup.open")))
      .append(HtmlChunk.nbsp())
      .append(HtmlChunk.text(getMouseShortcutText())).toString()
    return HintUtil.prepareHintText(htmlBodyText, hintHint)
  }

  private fun getMouseShortcutText(): @Nls String {
    val modifiersEx = if (OS.CURRENT == OS.macOS) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
    val shortcut = MouseShortcut(MouseEvent.BUTTON1, modifiersEx, 1)
    return KeymapUtil.getShortcutText(shortcut)
  }

  private fun hideIfVisible() {
    getHintInfoIfVisible()?.hint?.hide()
    hintInfo = null
  }

  private fun showHint(editor: Editor, offset: Int, component: JComponent): LightweightHint {
    val hint = LightweightHint(component)
    val position = editor.offsetToLogicalPosition(offset)
    val constraint = HintManager.ABOVE
    val p = HintManagerImpl.getHintPosition(hint, editor, position, constraint)
    val hintHint = HintManagerImpl.createHintHint(editor, p, hint, constraint)
      .setContentActive(false)
      .setShowImmediately(true)
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint, editor, p,
      HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
      0, false, hintHint
    )
    return hint
  }

  private fun getHintInfoIfVisible(): HintInfo? {
    return hintInfo?.takeIf { it.hint.isVisible }
  }

  private data class HintInfo(
    val hint: LightweightHint,
    val link: RangeHighlighterEx,
    val initialEvent: EditorMouseEvent,
  )
}
