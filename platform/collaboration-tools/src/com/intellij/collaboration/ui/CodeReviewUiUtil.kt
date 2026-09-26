// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.border.Border

@ApiStatus.Internal
object CodeReviewUiUtil {
  fun setupStandaloneEditorOutlineBorder(editor: EditorEx) {
    // shift from the left side and stretch the field vertically
    editor.setBorder(Borders.empty(2, 6, 2, 0))
    editor.component.border = EditorOutlineBorder()
    // repaint border
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) = editor.component.repaint()
      override fun focusLost(editor: Editor) = editor.component.repaint()
    })
  }
}

/**
 * Similar to [com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorderNew], but we can't use it because
 * of nested focus and because it's a UIResource
 */
private class EditorOutlineBorder : Border, ErrorBorderCapable {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val hasFocus = UIUtil.isFocusAncestor(c)

    val rect = Rectangle(x, y, width, height).also {
      val maxBorderThickness = DarculaUIUtil.BW.get()
      @Suppress("UseDPIAwareInsets")
      JBInsets.removeFrom(it, Insets(maxBorderThickness, maxBorderThickness, maxBorderThickness, maxBorderThickness))
    }
    // in both of those `rect` should be component bounds + padding + line border (lw)
    DarculaNewUIUtil.fillInsideComponentBorder(g, rect, c.background)
    DarculaNewUIUtil.paintComponentBorder(g, rect, DarculaUIUtil.getOutline(c as JComponent), hasFocus, c.isEnabled)
  }

  override fun getBorderInsets(c: Component): Insets {
    val bw = DarculaUIUtil.BW.float
    val lw = DarculaUIUtil.LW.float
    val padding = 1 // move the content away from the rounded corners to avoid clipping
    val inset = (bw + lw + padding).toInt()
    @Suppress("UseDPIAwareInsets")
    return Insets(inset, inset, inset, inset)
  }

  override fun isBorderOpaque(): Boolean = false
}
