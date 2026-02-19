// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.scale.DerivedScaleType
import com.intellij.util.IconUtil
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D
import javax.swing.Icon

class IconWithRectangularOverlay(
  main: Icon,
  overlay: Icon,
  private val overlayArea: Rectangle,
) : IconWithOverlay(main, overlay) {

  override fun replaceBy(replacer: IconReplacer) = IconWithRectangularOverlay(
    replacer.replaceIcon(mainIcon),
    replacer.replaceIcon(overlayIcon),
    overlayArea,
  )

  override fun copy() = IconWithRectangularOverlay(mainIcon, overlayIcon, overlayArea)

  override fun deepCopy() = IconWithRectangularOverlay(
    IconUtil.copy(mainIcon, ancestor = null),
    IconUtil.copy(overlayIcon, ancestor = null),
    overlayArea,
  )

  override fun getOverlayShape(x: Int, y: Int): Shape {
    val scale = getScale(DerivedScaleType.EFF_USR_SCALE).toFloat()
    return Rectangle2D.Float(
    x + scale * overlayArea.x,
    y + scale * overlayArea.y,
    scale * overlayArea.width,
    scale * overlayArea.height,
    )
  }

}
