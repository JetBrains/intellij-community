// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBCachingScalableIcon
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import kotlin.math.max

@ApiStatus.Internal
class RecentProjectLightDarkIcon(val light: Icon, val dark: Icon) : JBCachingScalableIcon<RecentProjectLightDarkIcon>() {
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    val icon = if (JBColor.isBright()) light else dark
    val scaledIcon = IconUtil.scale(icon, ancestor = c, scale)
    scaledIcon.paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int {
    val width = max(dark.iconWidth, light.iconWidth)
    return scaleVal(width.toDouble()).toInt()
  }

  override fun getIconHeight(): Int {
    val height = max(dark.iconHeight, light.iconHeight)
    return scaleVal(height.toDouble()).toInt()
  }

  override fun copy(): RecentProjectLightDarkIcon {
    val copy = RecentProjectLightDarkIcon(light, dark)
    copy.updateContextFrom(this)
    return copy
  }
}
