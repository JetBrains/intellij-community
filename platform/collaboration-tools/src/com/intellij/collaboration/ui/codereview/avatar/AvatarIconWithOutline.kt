// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.avatar

import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils.INNER_WIDTH
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils.OUTLINE_WIDTH
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import javax.swing.Icon

object CodeReviewAvatarUtils {
  internal const val INNER_WIDTH = 1
  internal const val OUTLINE_WIDTH = 2

  fun expectedIconHeight(size: Int = Avatar.Sizes.OUTLINED): Int =
    size + 2 * (INNER_WIDTH + OUTLINE_WIDTH)

  fun createIconWithOutline(avatarIcon: Icon, outlineColor: Color): Icon =
    AvatarIconWithOutline(avatarIcon, outlineColor)
}

/**
 * @param avatarIcon Avatar icon without any outlines, but scaled.
 */
private class AvatarIconWithOutline(private val avatarIcon: Icon, private val outlineColor: Color) : Icon {
  override fun getIconWidth(): Int = avatarIcon.iconWidth + 2 * (JBUI.scale(INNER_WIDTH + OUTLINE_WIDTH))
  override fun getIconHeight(): Int = avatarIcon.iconHeight + 2 * (JBUI.scale(INNER_WIDTH + OUTLINE_WIDTH))

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val g2d = g.create() as Graphics2D
    g2d.translate(x, y)

    val width = iconWidth.toFloat()
    val height = iconHeight.toFloat()

    val innerIconOffset = JBUI.scale(INNER_WIDTH + OUTLINE_WIDTH)
    val outlineThickness = JBUIScale.scale(OUTLINE_WIDTH.toFloat())

    try {
      GraphicsUtil.setupRoundedBorderAntialiasing(g2d)

      val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(Ellipse2D.Float(0f, 0f, width, height), false)

      val innerWidth = width - outlineThickness * 2
      val innerHeight = height - outlineThickness * 2
      border.append(Ellipse2D.Float(outlineThickness, outlineThickness, innerWidth, innerHeight), false)

      g2d.color = outlineColor
      g2d.fill(border)

      avatarIcon.paintIcon(c, g2d, innerIconOffset, innerIconOffset)
    }
    finally {
      g2d.dispose()
    }
  }
}
