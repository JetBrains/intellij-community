// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.reference.SoftReference
import com.intellij.ui.HintHint
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.system.OS
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal class InvisibleHyperlinkHintManager(private val editor: Editor) {

  private var hintRef: Reference<LightweightHint>? = null

  fun isInsideHint(e: EditorMouseEvent): Boolean {
    val hint = getVisibleHint()
    return hint != null && hint.isInsideHint(RelativePoint(e.mouseEvent))
  }

  fun showHint(offset: Int, action: () -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    hideIfVisible()
    val component = createHintLabel(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        action()
        hideIfVisible()
      }
    })
    val hint = showHint(editor, offset, component)
    hintRef = WeakReference(hint)
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
    getVisibleHint()?.hide()
    hintRef = null
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

  private fun getVisibleHint(): LightweightHint? {
    return SoftReference.dereference(hintRef)?.takeIf { it.isVisible }
  }
}
