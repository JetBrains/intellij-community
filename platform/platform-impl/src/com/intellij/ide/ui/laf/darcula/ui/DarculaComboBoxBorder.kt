// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComboBox
import javax.swing.border.Border
import javax.swing.plaf.UIResource

/**
 * ComboBox border for new UI themes. Uses own rendering only if [DarculaComboBoxUI.isNewBorderSupported], otherwise uses
 * border paint from DarculaComboBoxUI itself for backward compatibility (which can be removed later)
 */
@ApiStatus.Internal
open class DarculaComboBoxBorder : Border, ErrorBorderCapable, UIResource {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (c !is JComboBox<*>) {
      return
    }

    val ui = getLegacyComboBoxUI(c)

    if (ui == null) {
      paintBorderImpl(c, g, x, y, width, height)
    }
    else {
      ui.paintBorder(c, g, x, y, width, height)
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    if (c !is JComboBox<*>) {
      return JBInsets.emptyInsets()
    }

    val ui = getLegacyComboBoxUI(c)

    if (ui == null) {
      return when (getType(c)) {
        Type.TABLE_CELL_EDITOR, Type.COMPACT -> JBInsets.create(2, 3)
        Type.EMBEDDED -> JBUI.insets(2)
        Type.BORDERLESS -> JBUI.insets(1)
        Type.DEFAULT -> DarculaComboBoxUI.getDefaultComboBoxInsets()
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
  protected fun paintComboBoxBackground(g: Graphics2D, comboBox: JComboBox<*>, color: Color) {
    val r = Rectangle(comboBox.size)
    when (getType(comboBox)) {
      Type.EMBEDDED -> {
        val g2 = g.create() as Graphics2D
        try {
          g2.color = color
          g2.fillRect(r.x, r.y, r.width, r.height)
        }
        finally {
          g2.dispose()
        }
      }
      else -> {
        JBInsets.removeFrom(r, getBorderInsets(comboBox))
        DarculaNewUIUtil.fillInsideComponentBorder(g, r, color)
      }
    }
  }

  private fun paintBorderImpl(comboBox: JComboBox<*>, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val focused = DarculaComboBoxUI.hasComboBoxFocus(comboBox)
    val r = Rectangle(x, y, width, height)
    val g2 = g.create() as Graphics2D

    try {
      when (getType(comboBox)) {
        Type.TABLE_CELL_EDITOR, Type.EMBEDDED -> {
          DarculaUIUtil.paintCellEditorBorder(g2, comboBox, r, focused)
        }
        Type.BORDERLESS -> {
          paintBorderlessBorder(g2, comboBox, r, focused)
        }
        else -> {
          paintNormalBorder(g2, comboBox, r, focused)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintNormalBorder(g: Graphics2D, comboBox: JComboBox<*>, r: Rectangle, focused: Boolean) {
    JBInsets.removeFrom(r, getBorderInsets(comboBox))
    DarculaNewUIUtil.paintComponentBorder(g, r, DarculaUIUtil.getOutline(comboBox), focused, comboBox.isEnabled)
  }

  private fun paintBorderlessBorder(g: Graphics2D, comboBox: JComboBox<*>, r: Rectangle, focused: Boolean) {
    JBInsets.removeFrom(r, getBorderInsets(comboBox))
    DarculaNewUIUtil.paintComponentBorder(g, r, DarculaUIUtil.getOutline(comboBox), focused, comboBox.isEnabled, bw = DarculaUIUtil.LW.get())
  }

  /**
   * Returns DarculaComboBoxUI if it should be used as border instead of [DarculaComboBoxBorder] itself.
   * See [DarculaComboBoxUI.isNewBorderSupported] for details
   */
  private fun getLegacyComboBoxUI(comboBox: JComboBox<*>): DarculaComboBoxUI? {
    val ui = comboBox.ui as? DarculaComboBoxUI ?: return null
    val customBorder = comboBox.border !== ui && comboBox.border !is DarculaComboBoxBorder
    return if (customBorder || ui.isNewBorderSupported(comboBox)) null else ui
  }

  private fun getType(comboBox: JComboBox<*>): Type {
    return when {
      DarculaUIUtil.isTableCellEditor(comboBox) -> Type.TABLE_CELL_EDITOR
      DarculaUIUtil.isCompact(comboBox) -> Type.COMPACT
      comboBox.getClientProperty(ComboBox.IS_EMBEDDED_PROPERTY) == true -> Type.EMBEDDED
      DarculaUIUtil.isBorderless(comboBox) -> Type.BORDERLESS
      else -> Type.DEFAULT
    }
  }

  private enum class Type {
    DEFAULT,
    TABLE_CELL_EDITOR,
    COMPACT,
    EMBEDDED,
    BORDERLESS
  }
}
