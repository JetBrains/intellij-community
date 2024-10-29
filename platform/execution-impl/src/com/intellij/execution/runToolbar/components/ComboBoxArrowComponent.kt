// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.UIManager

@ApiStatus.Internal
class ComboBoxArrowComponent {
  fun getView(): JComponent = label


  val label = object : JLabel() {
    private val defaultIcon = ComboBoxArrowIcon()

    init {
      updateIcon()
    }

    override fun setEnabled(enabled: Boolean) {
      super.setEnabled(enabled)
      updateIcon()
    }

    private fun updateIcon() {
      /** according to { @see com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton.MyButtonModel } logic */
      icon = if (UIUtil.isUnderWin10LookAndFeel()) {
        if (isEnabled) {
          UIManager.getIcon("ComboBoxButton.arrowIcon") ?: AllIcons.General.ArrowDown
        }
        else {
          UIManager.getIcon("ComboBoxButton.arrowIconDisabled") ?: IconLoader.getDisabledIcon(AllIcons.General.ArrowDown)
        }
      }
      else defaultIcon
    }
  }
}

private class ComboBoxArrowIcon : Icon {
  val iconSize: Int
    get() = JBUIScale.scale(16)

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

      g2.color = JBUI.CurrentTheme.Arrow.foregroundColor(c.isEnabled)

      val arrow: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
      arrow.moveTo(JBUIScale.scale(3.5f).toDouble(), JBUIScale.scale(6f).toDouble())
      arrow.lineTo(JBUIScale.scale(12.5f).toDouble(), JBUIScale.scale(6f).toDouble())
      arrow.lineTo(JBUIScale.scale(8f).toDouble(), JBUIScale.scale(11f).toDouble())
      arrow.closePath()

      g2.fill(arrow)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getIconWidth(): Int {
    return iconSize
  }

  override fun getIconHeight(): Int {
    return iconSize
  }
}