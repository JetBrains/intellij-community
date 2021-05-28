// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.ui.IconDeferrer
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ImageLoader
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import com.intellij.util.ui.*
import org.imgscalr.Scalr
import org.jetbrains.annotations.SystemIndependent
import java.awt.Color
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
  }

  private val projectIcons = HashMap<String, MyIcon>()

  fun getProjectIcon(path: @SystemIndependent String, isDark: Boolean, generateFromName: Boolean = false): Icon {
    val icon = projectIcons.get(path)
    if (icon != null) {
      return icon.icon
    }
    if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
      return EmptyIcon.ICON_16
    }
    return IconDeferrer.getInstance().defer(EmptyIcon.ICON_16, Pair(path, isDark)) {
      val calculateIcon = calculateIcon(it.first, it.second)
      if (calculateIcon == null && generateFromName) {
        val name = RecentProjectsManagerBase.instanceEx.getProjectName(path)
        AvatarUtils.createRoundRectIcon(AvatarUtils.generateColoredAvatar(name, name, ProjectIconPalette), 20)
      }
      else calculateIcon
    }
  }

  fun getProjectOrAppIcon(path: @SystemIndependent String): Icon {
    return getProjectIcon(path, StartupUiUtil.isUnderDarcula())
  }

  private fun calculateIcon(path: @SystemIndependent String, isDark: Boolean): Icon? {
    var file = Paths.get(path, ".idea", if (isDark) "icon_dark.png" else "icon.png")
    var recolor = false
    if (isDark && !file.exists()) {
      file = Paths.get(path, ".idea", "icon.png")
      recolor = true
    }

    val fileInfo = file.basicAttributesIfExists() ?: return null
    val timestamp = fileInfo.lastModifiedTime().toMillis()

    var iconWrapper = projectIcons.get(path)
    if (iconWrapper != null && iconWrapper.timestamp == timestamp) {
      return iconWrapper.icon
    }

    try {
      var icon = createIcon(file) ?: return null
      if (recolor) {
        icon = IconLoader.getDarkIcon(icon, true)
      }

      iconWrapper = MyIcon(icon, timestamp)

      projectIcons.put(path, iconWrapper)
      return iconWrapper.icon
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }
}

private data class MyIcon(val icon: Icon, val timestamp: Long?)

private fun toRetinaAwareIcon(image: BufferedImage): Icon {
  return object : Icon {
    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      // [tav] todo: the icon is created in def screen scale
      if (UIUtil.isJreHiDPI()) {
        val newG = g.create(x, y, image.width, image.height) as Graphics2D
        val s = JBUIScale.sysScale()
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
      return if (UIUtil.isJreHiDPI()) (image.width / JBUIScale.sysScale()).toInt() else image.width
    }

    override fun getIconHeight(): Int {
      return if (UIUtil.isJreHiDPI()) (image.height / JBUIScale.sysScale()).toInt() else image.height
    }
  }
}

object ProjectIconPalette : ColorPalette() {

  override val gradients: Array<kotlin.Pair<Color, Color>>
    get() = arrayOf(
      JBColor(0xDB3D3C, 0xCE443C) to JBColor(0xFF8E42, 0xE77E41),
      JBColor(0xF57236, 0xE27237) to JBColor(0xFCBA3F, 0xE8A83E),
      JBColor(0x2BC8BB, 0x2DBCAD) to JBColor(0x36EBAE, 0x35D6A4),
      JBColor(0x359AF2, 0x3895E1) to JBColor(0x57DBFF, 0x51C5EA),
      JBColor(0x8379FB, 0x7B75E8) to JBColor(0x85A8FF, 0x7D99EB),
      JBColor(0x7E54B5, 0x7854AD) to JBColor(0x9486FF, 0x897AE6),
      JBColor(0xD63CC8, 0x8F4593) to JBColor(0xF582B9, 0xB572E3),
      JBColor(0x954294, 0xC840B9) to JBColor(0xC87DFF, 0xE074AE),
      JBColor(0xE75371, 0xD75370) to JBColor(0xFF78B5, 0xE96FA3)
    )
}