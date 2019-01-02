// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.ui.ProductIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Pair
import com.intellij.ui.IconDeferrer
import com.intellij.util.IconUtil
import com.intellij.util.ImageLoader
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import gnu.trove.THashMap
import org.imgscalr.Scalr
import org.jetbrains.annotations.SystemIndependent
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.net.MalformedURLException
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Icon

private val LOG = logger<RecentProjectIconHelper>()

internal class RecentProjectIconHelper {
  companion object {
    @JvmStatic
    fun createIcon(file: Path): Icon? {
      try {
        val image = ImageLoader.loadFromUrl(file.toUri().toURL()) ?: return null
        val targetSize = if (UIUtil.isRetina()) 32 else JBUI.pixScale(16f).toInt()
        return toRetinaAwareIcon(Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.ULTRA_QUALITY, targetSize))
      }
      catch (e: MalformedURLException) {
        LOG.debug(e)
      }
      return null
    }

    private fun toRetinaAwareIcon(image: BufferedImage): Icon {
      return object : Icon {
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
          // [tav] todo: the icon is created in def screen scale
          if (UIUtil.isJreHiDPI()) {
            val newG = g.create(x, y, image.width, image.height) as Graphics2D
            val s = JBUI.sysScale()
            newG.scale((1 / s).toDouble(), (1 / s).toDouble())
            newG.drawImage(image, (x / s).toInt(), (y / s).toInt(), null)
            newG.scale(1.0, 1.0)
            newG.dispose()
          }
          else {
            g.drawImage(image, x, y, null)
          }
        }

        override fun getIconWidth(): Int {
          return if (UIUtil.isJreHiDPI()) (image.width / JBUI.sysScale()).toInt() else image.width
        }

        override fun getIconHeight(): Int {
          return if (UIUtil.isJreHiDPI()) (image.height / JBUI.sysScale()).toInt() else image.height
        }
      }
    }
  }

  private val projectIcons = THashMap<String, MyIcon>()

  private val smallAppIcon by lazy {
    try {
      val appIcon = ProductIcons.getInstance().productIcon
      if (appIcon.iconWidth.toFloat() == JBUI.pixScale(16f) && appIcon.iconHeight.toFloat() == JBUI.pixScale(16f)) {
        return@lazy appIcon
      }
      else {
        var image = ImageUtil.toBufferedImage(IconUtil.toImage(appIcon))
        image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, if (UIUtil.isRetina()) 32 else JBUI.pixScale(16f).toInt())
        return@lazy toRetinaAwareIcon(image)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }

    EmptyIcon.ICON_16
  }

  fun getProjectIcon(path: @SystemIndependent String, isDark: Boolean): Icon? {
    val icon = projectIcons.get(path)
    return when {
      icon != null -> icon.icon
      else -> {
        IconDeferrer.getInstance().defer(EmptyIcon.ICON_16, Pair.create(path, isDark)) {
          calculateIcon(it.first, it.second)
        }
      }
    }
  }

  fun getProjectOrAppIcon(path: @SystemIndependent String): Icon {
    var icon = getProjectIcon(path, UIUtil.isUnderDarcula())
    if (icon != null) {
      return icon
    }

    if (UIUtil.isUnderDarcula()) {
      // no dark icon for this project
      icon = getProjectIcon(path, false)
      if (icon != null) {
        return icon
      }
    }

    return smallAppIcon
  }

  private fun calculateIcon(path: @SystemIndependent String, isDark: Boolean): Icon? {
    val file = Paths.get(path, ".idea", if (isDark) "icon_dark.png" else "icon.png")
    val fileInfo = file.basicAttributesIfExists() ?: return null
    val timestamp = fileInfo.lastModifiedTime().toMillis()

    var icon = projectIcons.get(path)
    if (icon != null && icon.timestamp == timestamp) {
      return icon.icon
    }

    try {
      icon = MyIcon(createIcon(file) ?: return null, timestamp)
      projectIcons.put(path, icon)
      return icon.icon
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }
}

private data class MyIcon(val icon: Icon, val timestamp: Long?)