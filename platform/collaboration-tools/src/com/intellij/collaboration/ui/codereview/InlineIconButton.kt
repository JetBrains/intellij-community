// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.BaseButtonBehavior
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.*
import java.awt.event.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.plaf.ComponentUI

class InlineIconButton(val icon: Icon,
                       val hoveredIcon: Icon = icon,
                       val disabledIcon: Icon = IconLoader.getDisabledIcon(icon),
                       @NlsContexts.Tooltip val tooltip: String? = null,
                       var shortcut: ShortcutSet? = null)
  : JComponent() {

  var actionListener: ActionListener? = null

  init {
    setUI(InlineIconButtonUI())
  }

  private class InlineIconButtonUI : ComponentUI() {

    private var buttonBehavior: BaseButtonBehavior? = null
    private var tooltipConnector: UiNotifyConnector? = null
    private var spaceKeyListener: KeyListener? = null

    override fun paint(g: Graphics, c: JComponent) {
      c as InlineIconButton
      val icon = getCurrentIcon(c)

      val g2 = g.create() as Graphics2D
      try {
        val r = Rectangle(c.getSize())
        JBInsets.removeFrom(r, c.insets)

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g2.translate(r.x, r.y)
        icon.paintIcon(c, g2, 0, 0)
      }
      finally {
        g2.dispose()
      }
    }

    override fun getMinimumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }

    override fun getPreferredSize(c: JComponent): Dimension {
      val icon = (c as InlineIconButton).icon
      val size = Dimension(icon.iconWidth, icon.iconHeight)
      JBInsets.addTo(size, c.insets)
      return size
    }

    override fun getMaximumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }

    private fun getCurrentIcon(c: InlineIconButton): Icon {
      if (!c.isEnabled) return c.disabledIcon
      if (buttonBehavior?.isHovered == true || c.hasFocus()) return c.hoveredIcon
      return c.icon
    }

    override fun installUI(c: JComponent) {
      c as InlineIconButton
      buttonBehavior = object : BaseButtonBehavior(c) {
        override fun execute(e: MouseEvent) {
          if (c.isEnabled) {
            c.actionListener?.actionPerformed(ActionEvent(e, ActionEvent.ACTION_PERFORMED, "execute", e.modifiers))
          }
        }
      }
      spaceKeyListener = object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          if (c.isEnabled && !e.isConsumed && e.modifiers == 0 && e.keyCode == KeyEvent.VK_SPACE) {
            c.actionListener?.actionPerformed(ActionEvent(e, ActionEvent.ACTION_PERFORMED, "execute", e.modifiers))
            e.consume()
            return
          }
        }
      }
      c.addKeyListener(spaceKeyListener)

      tooltipConnector = UiNotifyConnector(c, object : Activatable {
        override fun showNotify() {
          if (c.tooltip != null) {
            HelpTooltip()
              .setTitle(c.tooltip)
              .setShortcut(c.shortcut?.let { KeymapUtil.getFirstKeyboardShortcutText(it) })
              .installOn(c)
          }
        }

        override fun hideNotify() {
          HelpTooltip.dispose(c)
        }
      })

      c.isOpaque = false
      c.isFocusable = true
      c.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun uninstallUI(c: JComponent) {
      tooltipConnector?.let {
        Disposer.dispose(it)
      }
      tooltipConnector = null
      spaceKeyListener?.let {
        c.removeKeyListener(it)
      }
      spaceKeyListener = null
      buttonBehavior = null
      HelpTooltip.dispose(c)
    }
  }
}