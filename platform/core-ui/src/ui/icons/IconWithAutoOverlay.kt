// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Shape
import java.awt.geom.AffineTransform
import javax.swing.Icon

@ApiStatus.Internal
fun iconWithAutoOverlay(mainIcon: Icon, overlayIcon: Icon): Icon {
  return IconWithAutoOverlay(mainIcon, overlayIcon, (overlayIcon as? IconWithShape)?.getShape())
}

@ApiStatus.Internal
class IconWithAutoOverlay(mainIcon: Icon, overlayIcon: Icon, private val shape: Shape?) : IconWithOverlay(mainIcon, overlayIcon) {
  private var cachedShapeScale = 1.0f
  private var cachedShape: Shape? = shape
  
  override fun replaceBy(replacer: IconReplacer): LayeredIcon {
    return IconWithAutoOverlay(replacer.replaceIcon(mainIcon), replacer.replaceIcon(overlayIcon), shape)
  }

  override fun copy(): LayeredIcon {
    return IconWithAutoOverlay(mainIcon, overlayIcon, shape)
  }

  override fun deepCopy(): LayeredIcon {
    return IconWithAutoOverlay(IconUtil.copy(mainIcon, null), IconUtil.copy(overlayIcon, null), shape)
  }

  override fun getOverlayShape(x: Int, y: Int): Shape? {
    if (shape == null) return null
    val effectiveScale = JBUIScale.scale(scale)
    if (effectiveScale == cachedShapeScale) return cachedShape
    val transform = AffineTransform()
    transform.scale(effectiveScale.toDouble(), effectiveScale.toDouble())
    val result = transform.createTransformedShape(shape)
    cachedShapeScale = effectiveScale
    cachedShape = result
    return result
  }
}
