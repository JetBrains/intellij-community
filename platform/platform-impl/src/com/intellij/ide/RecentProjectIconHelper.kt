// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconDeferrer
import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.util.IconUtil
import com.intellij.util.ImageLoader
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.ui.*
import org.imgscalr.Scalr
import org.jetbrains.annotations.SystemIndependent
import java.awt.Color
import java.net.MalformedURLException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*
import javax.swing.Icon
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.math.max

private val LOG = logger<RecentProjectIconHelper>()

internal class RecentProjectIconHelper {
  companion object {
    private const val ideaDir = Project.DIRECTORY_STORE_FOLDER

    fun getDotIdeaPath(path: Path): Path {
      if (path.isDirectory() || path.parent == null) return path.resolve(ideaDir)

      val fileName = path.fileName.toString()

      val dotIndex = fileName.lastIndexOf('.')
      val fileNameWithoutExt = if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)

      return path.parent.resolve("$ideaDir/$ideaDir.$fileNameWithoutExt/$ideaDir")
    }

    fun getDotIdeaPath(path: String): Path? = try {
      getDotIdeaPath(Path.of(path))
    }
    catch (e: InvalidPathException) {
      null
    }

    @JvmStatic
    fun createIcon(file: Path): Icon? {
      try {
        if ("svg" == file.extension.lowercase(Locale.ENGLISH)) {
          return IconDeferrer.getInstance().defer(EmptyIcon.create(projectIconSize()), Pair(file.toAbsolutePath(), StartupUiUtil.isUnderDarcula())) {
            val icon = IconLoader.findIcon(file.toUri().toURL(), false) ?: return@defer null

            if (icon is ScaleContextAware) {
              icon.updateScaleContext(ScaleContext.create())
            }

            val iconSize = max(icon.iconWidth, icon.iconHeight)
            if (iconSize == projectIconSize()) return@defer icon
            return@defer IconUtil.scale(icon, null, projectIconSize().toFloat() / iconSize)
          }
        }
        val image = ImageLoader.loadFromUrl(file.toUri().toURL()) ?: return null
        val targetSize = if (UIUtil.isRetina()) 32 else JBUI.pixScale(16f).toInt()
        return IconUtil.toRetinaAwareIcon(Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.ULTRA_QUALITY, targetSize))
      }
      catch (e: MalformedURLException) {
        LOG.debug(e)
      }
      return null
    }

    @JvmStatic
    private val projectIcons = HashMap<String, MyIcon>()

    @JvmStatic
    fun refreshProjectIcon(path: @SystemIndependent String) {
      projectIcons.remove(path)
    }

    @JvmStatic
    fun projectIconSize() = Registry.intValue("ide.project.icon.size", 20)

    @JvmStatic
    fun generateProjectIcon(path: @SystemIndependent String): Icon {
      val projectManager = RecentProjectsManagerBase.instanceEx
      val displayName = projectManager.getDisplayName(path)
      val name = when {
        displayName == null -> projectManager.getProjectName(path)
        displayName.contains(",") -> iconTextForCommaSeparatedName(displayName)
        else -> displayName
      }
      return AvatarUtils.createRoundRectIcon(AvatarUtils.generateColoredAvatar(name, name, ProjectIconPalette), projectIconSize())
    }

    // Examples:
    // - "First, Second" => "FS"
    // - "First Project, Second Project" => "FS"
    private fun iconTextForCommaSeparatedName(name: String) =
      name.split(",")
        .take(2)
        .map { word -> word.firstOrNull { !it.isWhitespace() } ?: "" }
        .joinToString("")
        .uppercase(Locale.getDefault())

    private fun calculateIcon(path: @SystemIndependent String, isDark: Boolean): Icon? {
      val lookup = if (isDark) sequenceOf("icon_dark.svg", "icon.svg", "icon_dark.png", "icon.png") else sequenceOf("icon.svg", "icon.png")
      val file = lookup.map { getDotIdeaPath(path)?.resolve(it) }.filterNotNull().firstOrNull { it.exists() } ?: return null

      val fileInfo = file.basicAttributesIfExists() ?: return null
      val timestamp = fileInfo.lastModifiedTime().toMillis()

      val recolor = isDark && !file.nameWithoutExtension.endsWith("_dark")
      var iconWrapper = projectIcons[path]
      if (iconWrapper != null && iconWrapper.timestamp == timestamp) {
        return iconWrapper.icon
      }

      try {
        var icon = createIcon(file) ?: return null
        if (recolor) {
          icon = IconLoader.getDarkIcon(icon, true)
        }

        iconWrapper = MyIcon(icon, timestamp)

        projectIcons[path] = iconWrapper
        return iconWrapper.icon
      }
      catch (e: Exception) {
        LOG.error(e)
      }
      return null
    }
  }

  fun getProjectIcon(path: @SystemIndependent String, generateFromName: Boolean = false): Icon {
    val icon = projectIcons[path]
    if (icon != null) {
      return icon.icon
    }
    if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
      return EmptyIcon.create(projectIconSize())
    }
    return IconDeferrer.getInstance().deferAutoUpdatable(EmptyIcon.create(projectIconSize()), Pair(path, false)) {
      val calculateIcon = calculateIcon(path = it.first, isDark = it.second)
      if (calculateIcon == null && generateFromName) {
        generateProjectIcon(path)
      }
      else calculateIcon
    }
  }

  fun getProjectOrAppIcon(path: @SystemIndependent String): Icon {
    return getProjectIcon(path)
  }
}

private data class MyIcon(val icon: Icon, val timestamp: Long?)

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
