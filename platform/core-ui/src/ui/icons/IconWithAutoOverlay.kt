@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Shape
import javax.swing.Icon

/**
 * Creates and returns a new icon created by overlaying `overlayIcon` on top of `mainIcon`.
 * 
 * The icons should have the same size (use transparent padding if needed).
 * The overlay icon must be backed by an SVG file for automatic shape computation to work.
 * If it's not the case, an `IllegalArgumentException` is thrown.
 */
@ApiStatus.Experimental
fun iconWithOverlay(mainIcon: Icon, overlayIcon: Icon): Icon {
  return IconManager.getInstance().createIconWithOverlay(mainIcon, overlayIcon)
}

/**
 * Use [iconWithOverlay] instead, even in the monorepo.
 * 
 * This is for low-level internal use only, and only exists to reduce the temptation to use the constructor directly.
 */
@ApiStatus.Internal
object IconWithAutoOverlayLowLevelFactory {
  fun createIconWithAutoOverlay(mainIcon: Icon, overlayIcon: Icon): Icon {
    return IconWithAutoOverlay(mainIcon, overlayIcon)
  }
}

@ApiStatus.Internal
class IconWithAutoOverlay internal constructor(mainIcon: Icon, overlayIcon: Icon) : IconWithOverlay(mainIcon, overlayIcon) {
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
