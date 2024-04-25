@file:Internal
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.ui.*
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import kotlin.math.min

internal fun getIconData(icon: Icon?): CachedIconPresentation? {
  if (icon == null) return null
  return when (icon) {
    is LayeredIcon -> getIconData(getBiggestLayer(icon))
    is RowIcon -> getIconData(getBiggestRow(icon))
    is RetrievableIcon -> getIconData(icon.retrieveIcon())
    is CachedImageIcon -> getIconData(icon.getCoords())
    else -> null
  }
}

private fun getIconData(iconCoords: Pair<String, ClassLoader>?): CachedIconPresentation? {
  if (iconCoords == null) return null
  val path = iconCoords.first
  val classLoader = iconCoords.second
  val iconManager = IconManager.getInstance()
  val classLoaderCoords = iconManager.getPluginAndModuleId(classLoader)
  return CachedIconPresentation(path, classLoaderCoords.first, classLoaderCoords.second)
}

private fun getBiggestLayer(icon: LayeredIcon): Icon? =
  icon.allLayers.asSequence().filterNotNull().maxByOrNull { it.size }

private fun getBiggestRow(icon: RowIcon): Icon? =
  icon.allIcons.maxByOrNull { it.size }

private val Icon.size: Int
  get() = min(iconWidth, iconHeight)

internal fun getLoadingIcon(iconData: CachedIconPresentation?): Icon? {
  if (iconData == null) return null
  val iconManager = IconManager.getInstance()
  return iconManager.createDeferredIcon(
    AnimatedIcon.Default.INSTANCE,
    iconData,
  ) {
    val classLoader = iconManager.getClassLoader(iconData.plugin, iconData.module)
    if (classLoader == null) {
      AnimatedIcon.Default.INSTANCE
    }
    else try {
      LoadingIcon(iconManager.getIcon(iconData.path, classLoader))
    }
    catch (e: Exception) {
      AnimatedIcon.Default.INSTANCE
    }
  }
}

private class LoadingIcon(private val delegate: Icon) : Icon, RetrievableIcon {
  override fun retrieveIcon(): Icon = delegate

  override fun getIconWidth(): Int = delegate.iconWidth

  override fun getIconHeight(): Int = delegate.iconHeight

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    GraphicsUtil.paintWithAlpha(g, 0.5f) {
      delegate.paintIcon(c, g, x, y)
    }
  }
}
