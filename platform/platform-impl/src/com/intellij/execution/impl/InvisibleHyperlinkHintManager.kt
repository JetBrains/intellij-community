// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.reference.SoftReference
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.system.OS
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

internal class InvisibleHyperlinkHintManager(private val editor: Editor) {

  private var hintRef: Reference<LightweightHint>? = null

  fun isInsideHint(e: EditorMouseEvent): Boolean {
    val hint = SoftReference.dereference(hintRef) ?: return false
    return hint.isInsideHint(RelativePoint(e.mouseEvent))
  }

  fun showHint(hostOffset: Int, action: () -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    hideIfVisible()
    val hyperlinkListener = object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        action()
        hideIfVisible()
      }
    }
    val component = HintUtil.createInformationLabel(getHtmlText(), hyperlinkListener, null, null).also {
      it.border = JBUI.Borders.empty()
    }
    val hint = showHint(editor, hostOffset, component)
    hintRef = WeakReference(hint)
  }

  private fun hideIfVisible() {
    SoftReference.dereference(hintRef)?.let {
      if (it.isVisible) {
        it.hide()
      }
    }
    hintRef = null
  }

  private fun getHtmlText(): @Nls String {
    val mouseShortcut = KeymapUtil.parseMouseShortcut(if (OS.CURRENT == OS.macOS) "meta button1" else "ctrl button1")
    val text = KeymapTextContext().getMouseShortcutText(mouseShortcut)
    val message = HtmlBuilder()
      .append(HtmlChunk.link("", IdeBundle.message("editor.invisible.link.popup.open")))
      .append(HtmlChunk.nbsp())
      .append(HtmlChunk.text(text)).toString()
    return HintUtil.prepareHintText(message, HintUtil.getInformationHint())
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

}
