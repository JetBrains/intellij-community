// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.border.Border
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.properties.Delegates.observable

/**
 * A custom button with round corners and hovered/pressed state
 */
internal class PillButton : AbstractButton() {
  var rolloverBackground: Color? by property(ROLLOVER_BACKGROUND_PROPERTY, null)
  var pressedBackground: Color? by property(PRESSED_BACKGROUND_PROPERTY, null)

  init {
    setModel(DefaultButtonModel())
    setUI(PillButtonUI())

    alignmentX = LEFT_ALIGNMENT
    alignmentY = CENTER_ALIGNMENT
  }

  private fun <T> property(propertyName: String, initial: T) = observable(initial) { _, old, new ->
    firePropertyChange(propertyName, old, new)
  }

  companion object {
    const val ROLLOVER_BACKGROUND_PROPERTY: String = "rolloverBackground"
    const val PRESSED_BACKGROUND_PROPERTY: String = "pressedBackground"
  }
}

internal fun PillButton.setBorderColor(color: Color? = null) {
  border = PillButtonBorder(color)
}

private class PillButtonUI : BasicButtonUI() {
  override fun getPropertyPrefix(): String = PROPERTY_PREFIX

  override fun installDefaults(b: AbstractButton) {
    LookAndFeel.installProperty(b, "opaque", false)
    LookAndFeel.installProperty(b, "rolloverEnabled", true)
    LookAndFeel.installProperty(b, "iconTextGap", 4)
    if (b.font == null || b.font is UIResource) {
      b.font = UIUtil.getLabelFont()
    }
    b.background = CodeReviewColorUtil.Reaction.background
    if (b.border == null || b.border is UIResource) {
      b.border = UIResourceBorder(PillButtonBorder())
    }
    if (b !is PillButton) return
    if (b.margin == null || b.margin is UIResource) {
      b.margin = JBInsets.create(1, 6).asUIResource()
    }
    b.rolloverBackground = CodeReviewColorUtil.Reaction.backgroundHovered
    b.pressedBackground = CodeReviewColorUtil.Reaction.backgroundPressed
  }

  override fun getPreferredSize(c: JComponent): Dimension =
    super.getPreferredSize(c).also {
      if (c is AbstractButton) JBInsets.addTo(it, c.margin)
    }

  override fun paint(g: Graphics, c: JComponent) {
    val button = c as? PillButton ?: return
    if (button.isContentAreaFilled) {
      paintBackground(g, c)
    }
    super.paint(g, c)
  }

  private fun paintBackground(g: Graphics, c: PillButton) {
    val r = Rectangle(c.size)
    JBInsets.removeFrom(r, c.insets)
    val g2d = g.create(r.x, r.y, r.width, r.height) as Graphics2D
    try {
      GraphicsUtil.setupAAPainting(g2d)
      val arc = r.height
      g2d.color = getBackgroundColor(c)
      g2d.fill(RoundRectangle2D.Float(0f, 0f, r.width.toFloat(), r.height.toFloat(), arc.toFloat(), arc.toFloat()))
    }
    finally {
      g2d.dispose()
    }
  }

  private fun getBackgroundColor(c: PillButton): Color? {
    val model: ButtonModel = c.model
    var background: Color? = null
    if (c.isRolloverEnabled && c.isEnabled) {
      if (model.isRollover) {
        background = c.rolloverBackground
      }
      else if (model.isArmed && model.isPressed) {
        background = c.pressedBackground
      }
    }
    return background ?: c.background
  }

  companion object {
    private const val PROPERTY_PREFIX = "PillButton."
  }
}

/**
 * Similar to [com.intellij.ui.RoundedLineBorder], but uses component height as arc and uses component background color by default
 */
private class PillButtonBorder(private val color: Color? = null) : AbstractBorder() {
  private val thickness: Int = 1

  override fun getBorderInsets(c: Component, insets: Insets): Insets {
    if (c !is PillButton) return insets
    insets.set(thickness, thickness, thickness, thickness)
    return insets
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2d = g.create(x, y, width, height) as Graphics2D
    try {
      g2d.color = color ?: c.background ?: return
      GraphicsUtil.setupRoundedBorderAntialiasing(g2d)
      val arc = height.toFloat()
      val border: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(RoundRectangle2D.Float(0f,
                                           0f,
                                           width.toFloat(),
                                           height.toFloat(),
                                           arc,
                                           arc), false)

      val coordinateDelta = thickness
      val sizeDelta = thickness * 2
      border.append(RoundRectangle2D.Float(coordinateDelta.toFloat(),
                                           coordinateDelta.toFloat(),
                                           width.toFloat() - sizeDelta,
                                           height.toFloat() - sizeDelta,
                                           arc - sizeDelta,
                                           arc - sizeDelta), false)
      g2d.fill(border)
    }
    finally {
      g2d.dispose()
    }
  }
}

private class UIResourceBorder(delegate: Border) : Border by delegate, UIResource