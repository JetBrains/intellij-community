// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * @param icon  the icon to scale
 * @param scale the scale factor
 * @return the scaled icon
 */
@ApiStatus.Internal
fun scaleIconOrLoadCustomVersion(icon: Icon, scale: Float): Icon {
  if (icon is CachedImageIcon) {
    val oldWidth = icon.getIconWidth()
    val oldHeight = icon.getIconHeight()
    val newWidth = (scale * oldWidth).roundToInt()
    val newHeight = (scale * oldHeight).roundToInt()
    if (oldWidth == newWidth && oldHeight == newHeight) {
      return icon
    }

    val version = loadIconCustomVersion(icon = icon, width = newWidth, height = newHeight)
    if (version != null) {
      return version
    }
  }

  return if (icon is ScalableIcon) icon.scale(scale) else IconUtil.scale(icon = icon, ancestor = null, scale = scale)
}

private fun loadIconCustomVersion(icon: CachedImageIcon, width: Int, height: Int): Icon? {
  val coords = icon.resolver?.getCoords() ?: return null
  val path = coords.first
  if (!path.endsWith(".svg")) {
    return null
  }

  val modifiedPath = "${path.substring(0, path.length - 4)}@${width}x$height.svg"
  val foundIcon = IconLoader.findIcon(path = modifiedPath, classLoader = coords.second) ?: return null
  if (foundIcon is CachedImageIcon &&
      foundIcon.getIconWidth() == JBUIScale.scale(width) && foundIcon.getIconHeight() == JBUIScale.scale(height)) {
    return foundIcon.withAnotherIconModifications(icon)
  }
  return null
}

/** @param size the size before system scaling (without JBUIScale.scale)
 */
@ApiStatus.Internal
fun loadIconCustomVersionOrScale(icon: ScalableIcon, size: Int): Icon {
  if (icon.iconWidth == JBUIScale.scale(size)) {
    return icon
  }

  var cachedIcon: Icon = icon
  if (cachedIcon !is CachedImageIcon && cachedIcon is RetrievableIcon) {
    cachedIcon = cachedIcon.retrieveIcon()
  }
  if (cachedIcon is CachedImageIcon) {
    val version = loadIconCustomVersion(icon = cachedIcon, width = size, height = size)
    if (version != null) {
      return version
    }
  }
  return icon.scale(JBUIScale.scale(1.0f) * size / icon.iconWidth)
}