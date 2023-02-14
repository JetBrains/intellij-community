/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.SwingConstants

/** Public copy of MySeparator class from ActionToolbarImpl. Draws a separator in toolbar.*/
class MySeparator(private val myText: String?) : JComponent() {

    private var myOrientation: Int = SwingConstants.HORIZONTAL

    init {
        font = JBUI.Fonts.toolbarSmallComboBoxFont()
    }

    override fun getPreferredSize(): Dimension {
        val gap = JBUI.scale(2)
        val center = JBUI.scale(3)
        val width = gap * 2 + center
        val height = JBUI.scale(24)

        if (myOrientation != SwingConstants.HORIZONTAL) {
            return JBDimension(height, width, true)
        }

        return if (myText != null) {
            val fontMetrics = getFontMetrics(font)
            val textWidth = getTextWidth(fontMetrics, myText, graphics)
            JBDimension(width + gap * 2 + textWidth, Math.max(fontMetrics.height, height), true)
        } else {
            JBDimension(width, height, true)
        }
    }

    private fun getMaxButtonHeight(): Int {
        return ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE.height
    }

    private fun getMaxButtonWidth(): Int {
        return ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE.width
    }

    override fun paintComponent(g: Graphics) {
        if (parent == null) return

        val gap = JBUI.scale(2)
        val center = JBUI.scale(3)
        val offset: Int = if (myOrientation == SwingConstants.HORIZONTAL) {
            parent.height - getMaxButtonHeight() - 1
        } else {
            parent.width - getMaxButtonWidth() - 1
        }

        g.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        if (myOrientation == SwingConstants.HORIZONTAL) {
            val y2 = parent.height - gap * 2 - offset
            LinePainter2D.paint(g as Graphics2D, center.toDouble(), gap.toDouble(), center.toDouble(), y2.toDouble())

            if (myText != null) {
                val fontMetrics = getFontMetrics(font)
                val top = (height - fontMetrics.height) / 2
                UISettings.setupAntialiasing(g)
                g.setColor(JBColor.foreground())
                g.drawString(myText, gap * 2 + center + gap, top + fontMetrics.ascent)
            }
        } else {
            LinePainter2D.paint(g as Graphics2D, gap.toDouble(), center.toDouble(), (parent.width - gap * 2 - offset).toDouble(), center.toDouble())
        }
    }

    private fun getTextWidth(fontMetrics: FontMetrics, text: String, graphics: Graphics?): Int {
        if (graphics == null) {
            return fontMetrics.stringWidth(text)
        }

        val g = graphics.create()
        try {
            UISettings.setupAntialiasing(g)
            return fontMetrics.getStringBounds(text, g).bounds.width
        } finally {
            g.dispose()
        }
    }
}
