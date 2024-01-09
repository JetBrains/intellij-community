// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.fillInsideComponentBorder
import com.intellij.ide.ui.laf.darcula.paintComponentBorder
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import java.awt.*
import javax.swing.JComboBox
import javax.swing.border.Border
import javax.swing.plaf.UIResource

/**
 * ComboBox border for new UI themes. Uses own rendering only if [DarculaComboBoxUI.isNewBorderSupported], otherwise uses
 * border paint from DarculaComboBoxUI itself for backward compatibility (which can be removed later)
 */
open class DarculaComboBoxBorder : Border, ErrorBorderCapable, UIResource {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val ui = getLegacyComboBoxUI(c)

    if (ui == null) {
      paintBorderImpl(c, g, x, y, width, height)
    }
    else {
      ui.paintBorder(c, g, x, y, width, height)
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    val ui = getLegacyComboBoxUI(c)

    if (ui == null) {
      return when {
        DarculaUIUtil.isTableCellEditor(c) || DarculaUIUtil.isCompact(c) -> JBInsets.create(2, 3)
        DarculaUIUtil.isBorderless(c) -> JBInsets.emptyInsets()
        else -> DarculaComboBoxUI.getDefaultComboBoxInsets()
      }
    }
    else {
      return ui.getBorderInsets(c)
    }
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }

  /**
   * Paints background of combobox. Should take into account shape of the border (like rounded borders and insets)
   */
  fun paintComboBoxBackground(g: Graphics2D, comboBox: JComboBox<*>, color: Color) {
    val r = Rectangle(comboBox.size)
    JBInsets.removeFrom(r, getBorderInsets(comboBox))
    fillInsideComponentBorder(g, r, color)
  }

  private fun paintBorderImpl(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (c !is JComboBox<*>) {
      return
    }

    val focused = DarculaComboBoxUI.hasComboBoxFocus(c)
    val r = Rectangle(x, y, width, height)
    val g2 = g.create() as Graphics2D

    try {
      if (DarculaUIUtil.isTableCellEditor(c)) {
        DarculaUIUtil.paintCellEditorBorder(g2, c, r, focused)
        return
      }

      paintNormalBorder(g2, c, r, focused)
    }
    finally {
      g2.dispose()
    }
  }

  protected fun paintNormalBorder(g: Graphics2D, comboBox: JComboBox<*>, r: Rectangle, focused: Boolean) {
    JBInsets.removeFrom(r, getBorderInsets(comboBox))
    paintComponentBorder(g, r, DarculaUIUtil.getOutline(comboBox), focused, comboBox.isEnabled)
  }

  /**
   * Returns DarculaComboBoxUI if it should be used as border instead of [DarculaComboBoxBorder] itself.
   * See [DarculaComboBoxUI.isNewBorderSupported] for details
   */
  private fun getLegacyComboBoxUI(c: Component): DarculaComboBoxUI? {
    val comboBox = c as? JComboBox<*> ?: return null
    val ui = comboBox.ui as? DarculaComboBoxUI ?: return null
    return if (ui.isNewBorderSupported(comboBox)) null else ui
  }
}
