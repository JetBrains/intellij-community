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
  return IconWithAutoOverlay(mainIcon, overlayIcon)
}

@ApiStatus.Internal
class IconWithAutoOverlay(mainIcon: Icon, overlayIcon: Icon) : IconWithOverlay(mainIcon, overlayIcon) {
  init {
    if (overlayIcon !is IconWithShape) {
      throw IllegalArgumentException("The overlayIcon must be an IconWithShape, but got ${overlayIcon.javaClass}: $overlayIcon")
    }
  }

  override fun replaceBy(replacer: IconReplacer): LayeredIcon {
    return IconWithAutoOverlay(replacer.replaceIcon(mainIcon), replacer.replaceIcon(overlayIcon))
  }

  override fun copy(): LayeredIcon {
    return IconWithAutoOverlay(mainIcon, overlayIcon)
  }

  override fun deepCopy(): LayeredIcon {
    return IconWithAutoOverlay(IconUtil.copy(mainIcon, null), IconUtil.copy(overlayIcon, null))
  }

  override fun getOverlayShape(x: Int, y: Int): Shape? {
    return (overlayIcon as IconWithShape).getShape()
  }
}
