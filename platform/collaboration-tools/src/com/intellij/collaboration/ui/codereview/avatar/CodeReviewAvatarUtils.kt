// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.avatar

import com.intellij.ui.LayeredIcon
import com.intellij.ui.RoundedIcon
import com.intellij.util.IconUtil
import com.intellij.util.ui.CenteredIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import javax.swing.Icon

object CodeReviewAvatarUtils {
  val OUTLINE_WIDTH: Int
    get() = JBUI.scale(3)

  fun outlineCircleIcon(icon: Icon, outlineColor: Color?): Icon {
    val iconSize = icon.iconWidth
    val outlineIconSize = iconSize + 2 * OUTLINE_WIDTH
    val colorImage = outlineColor?.let {
      val result = BufferedImage(outlineIconSize, outlineIconSize, TYPE_INT_RGB)
      val g2d = result.createGraphics()
      try {
        g2d.color = it
        g2d.fillRect(0, 0, result.width, result.height)
      }
      finally {
        g2d.dispose()
      }
      result
    }
    val colorIcon = colorImage?.let { RoundedIcon(it, 1.0, true) }
                    ?: EmptyIcon.create(outlineIconSize)
    val innerIcon =
      if (outlineColor != null) {
        icon
      }
      else {
        // make inner icon one pixel bigger if outline is empty,
        // so that outlined and non-outlined icons can stay together and looks good
        resizeIconScaled(icon, iconSize + JBUI.scale(1))
      }
    return LayeredIcon.layeredIcon { arrayOf(colorIcon, CenteredIcon(innerIcon, outlineIconSize, outlineIconSize, false)) }
  }

  private fun resizeIconScaled(icon: Icon, size: Int): Icon {
    val scale = size.toFloat() / icon.iconWidth.toFloat()
    return IconUtil.scale(icon, null, scale)
  }
}