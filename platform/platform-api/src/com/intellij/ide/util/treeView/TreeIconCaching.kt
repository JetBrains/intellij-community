// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.icons.AllIcons
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.ImageDescriptor
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon
import kotlin.math.min

internal fun getIconData(icon: Icon?): CachedIconPresentation? {
  val cachedIcon = findCachedImageIcon(icon) ?: return null

  if (cachedIcon.imageFlags and ImageDescriptor.NO_TREE_PRESENTATION_CACHE != 0) {
    return null
  }
  
  return cachedIcon.getCoords()?.let {
    getIconData(it)
  }
}

@Internal
fun findCachedImageIcon(icon: Icon?): CachedImageIcon? {
  return when (icon) {
    null -> null
    is LayeredIcon -> findCachedImageIcon(getBiggestLayer(icon))
    is RowIcon -> findCachedImageIcon(getBiggestRow(icon))
    is RetrievableIcon -> findCachedImageIcon(icon.retrieveIcon())
    is CachedImageIcon -> icon
    else -> null
  }
}

private fun getIconData(iconCoords: Pair<String, ClassLoader>): CachedIconPresentation {
  val path = iconCoords.first
  val classLoader = iconCoords.second
  val iconManager = IconManager.getInstance()
  val classLoaderCoords = iconManager.getPluginAndModuleId(classLoader)
  return CachedIconPresentation(path, classLoaderCoords.first, classLoaderCoords.second)
}

private fun getBiggestLayer(icon: LayeredIcon): Icon? = icon.allLayers.asSequence().filterNotNull().maxByOrNull { it.size }

private fun getBiggestRow(icon: RowIcon): Icon? =
  icon.allIcons.maxByOrNull { it.size }

private val Icon.size: Int
  get() = min(iconWidth, iconHeight)

private val DEFAULT_ICON = AllIcons.FileTypes.Unknown

@get:Internal
val CachedPresentationData.icon: Icon get() = getLoadingIcon(iconData)

private fun getLoadingIcon(iconData: CachedIconPresentation?): Icon {
  if (iconData == null) return DEFAULT_ICON
  val iconManager = IconManager.getInstance()
  return iconManager.createDeferredIcon(
    DEFAULT_ICON,
    iconData,
  ) {
    val classLoader = iconManager.getClassLoader(iconData.plugin, iconData.module)
    if (classLoader == null) {
      DEFAULT_ICON
    }
    else try {
      iconManager.getIcon(iconData.path, classLoader)
    }
    catch (e: Exception) {
      DEFAULT_ICON
    }
  }
}
