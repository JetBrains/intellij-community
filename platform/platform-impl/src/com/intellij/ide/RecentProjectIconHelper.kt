// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconDeferrer
import com.intellij.ui.JBColor
import com.intellij.ui.paint.withTxAndClipAligned
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ImageLoader
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.ui.*
import org.imgscalr.Scalr
import org.jetbrains.annotations.SystemIndependent
import java.awt.*
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*
import javax.swing.Icon
import kotlin.io.path.extension
import kotlin.math.max

private val LOG = logger<RecentProjectIconHelper>()

private fun unscaledProjectIconSize() = Registry.intValue("ide.project.icon.size", 20)

private fun userScaledProjectIconSize() = JBUIScale.scale(unscaledProjectIconSize())

internal class RecentProjectIconHelper {
  companion object {
    private const val ideaDir = Project.DIRECTORY_STORE_FOLDER

    private fun getDotIdeaPath(path: Path): Path {
      if (path.isDirectory() || path.parent == null) return path.resolve(ideaDir)

      val fileName = path.fileName.toString()

      val dotIndex = fileName.lastIndexOf('.')
      val fileNameWithoutExt = if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)

      return path.parent.resolve("$ideaDir/$ideaDir.$fileNameWithoutExt/$ideaDir")
    }

    fun getDotIdeaPath(path: String): Path? {
      return try {
        getDotIdeaPath(Path.of(path))
      }
      catch (e: InvalidPathException) {
        null
      }
    }

    @JvmStatic
    fun createIcon(file: Path): Icon = ProjectFileIcon(loadIconFile(file), userScaledProjectIconSize())

    @JvmStatic
    private val projectIconsCache = HashMap<String, ProjectIcon>()

    @JvmStatic
    fun refreshProjectIcon(path: @SystemIndependent String) {
      projectIconsCache.remove(path)
    }

    @JvmStatic
    fun projectIconSize() = userScaledProjectIconSize()

    @JvmStatic
    fun generateProjectIcon(path: @SystemIndependent String, isProjectValid: Boolean): Icon {
      val projectManager = RecentProjectsManagerBase.getInstanceEx()
      val displayName = projectManager.getDisplayName(path)
      val name = when {
        displayName == null -> projectManager.getProjectName(path)
        displayName.contains(",") -> iconTextForCommaSeparatedName(displayName)
        else -> displayName
      }
      var generatedProjectIcon: Icon = JBUIScale.scaleIcon(AvatarIcon(unscaledProjectIconSize(), 0.3, name, name, ProjectIconPalette))

      if (!isProjectValid) {
        generatedProjectIcon = IconUtil.desaturate(generatedProjectIcon)
      }

      projectIconsCache[path] = ProjectIcon(generatedProjectIcon, isProjectValid, projectIconSize())

      return generatedProjectIcon
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

    private fun getCustomIcon(path: @SystemIndependent String, isProjectValid: Boolean): Icon? {
      val lookup = sequenceOf("icon.svg", "icon.png")
      val file = lookup.map { getDotIdeaPath(path)?.resolve(it) }.filterNotNull().firstOrNull { it.exists() } ?: return null

      val fileInfo = file.basicAttributesIfExists() ?: return null
      val timestamp = fileInfo.lastModifiedTime().toMillis()

      var iconWrapper = projectIconsCache[path]
      if (iconWrapper != null && isCachedIcon(iconWrapper, isProjectValid, timestamp)) {
        return iconWrapper.icon
      }

      var icon = createIcon(file)
      if (!isProjectValid) {
        icon = IconUtil.desaturate(icon)
      }

      iconWrapper = ProjectIcon(icon, isProjectValid, projectIconSize(), timestamp)
      projectIconsCache[path] = iconWrapper

      return iconWrapper.icon
    }

    private fun getGeneratedProjectIcon(path: @SystemIndependent String, isProjectValid: Boolean): Icon {
      val projectIcon = projectIconsCache[path]
      if (projectIcon != null && isCachedIcon(projectIcon, isProjectValid)) {
        return projectIcon.icon
      }

      return generateProjectIcon(path, isProjectValid)
    }

    private fun isCachedIcon(icon: ProjectIcon, isProjectValid: Boolean, timestamp: Long? = null): Boolean {
      val isCached = icon.isProjectValid == isProjectValid && icon.lastUsedProjectIconSize == projectIconSize()
      return if (timestamp == null) isCached else isCached && icon.timestamp == timestamp
    }
  }

  fun getProjectIcon(path: @SystemIndependent String, isProjectValid: Boolean = true): Icon {
    if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
      return EmptyIcon.create(projectIconSize())
    }

    return IconDeferrer.getInstance().defer(EmptyIcon.create(projectIconSize()), Pair(path, isProjectValid)) {
      return@defer getCustomIcon(path = it.first, isProjectValid = it.second)
                                ?: getGeneratedProjectIcon(path = it.first, isProjectValid = it.second)
    }
  }
}

private data class ProjectIcon(
  val icon: Icon,
  val isProjectValid: Boolean,
  val lastUsedProjectIconSize: Int,
  val timestamp: Long? = null
)

private class ProjectFileIcon(
  private val iconData: IconData,
  private val userScaledSize: Int,
) : JBCachingScalableIcon<ProjectFileIcon>() {

  private var cachedIcon: Icon? = null
  private var cachedIconSysScale: Float? = null
  private var cachedIconPixScale: Float? = null

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val sysScale = JBUIScale.sysScale(g)
    g as Graphics2D
    val pixScale = JBUI.pixScale(g.deviceConfiguration)
    val cachedIcon = this.cachedIcon
    val delegate = if (
      cachedIcon != null &&
      cachedIconSysScale == sysScale &&
      cachedIconPixScale == pixScale
    ) {
      cachedIcon
    } else {
      iconData.getScaledIcon(sysScale, pixScale).also {
        this.cachedIcon = it
        this.cachedIconSysScale = sysScale
        this.cachedIconPixScale = pixScale
      }
    }
    withTxAndClipAligned(g, x, y, delegate.iconWidth, delegate.iconHeight) { ag ->
      delegate.paintIcon(c, ag, 0, 0)
    }
  }

  override fun getIconWidth(): Int = userScaledSize

  override fun getIconHeight(): Int = userScaledSize

  override fun copy(): ProjectFileIcon = ProjectFileIcon(iconData, userScaledSize)

}

private fun loadIconFile(file: Path): IconData = try {
  if ("svg" == file.extension.lowercase()) {
    SvgIconData(IconLoader.findIcon(file.toUri().toURL(), false), userScaledProjectIconSize())
  } else {
    PngIconData(ImageLoader.loadFromUrl(file.toUri().toURL()), userScaledProjectIconSize())
  }
} catch (e: Exception) {
  LOG.debug(e)
  EmptyIconData(userScaledProjectIconSize())
}

private sealed class IconData(protected val userScaledSize: Int) {

  abstract fun getScaledIcon(sysScale: Float, pixScale: Float): Icon

  protected fun emptyIcon(): Icon = EmptyIcon.create(userScaledSize)

}

private class SvgIconData(private val originalIcon: Icon?, userScaledSize: Int) : IconData(userScaledSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon {
    if (originalIcon == null) {
      return emptyIcon()
    }
    if (originalIcon is ScaleContextAware) {
      originalIcon.updateScaleContext(ScaleContext.create(ScaleType.SYS_SCALE.of(sysScale.toDouble())))
    }
    val iconSize = max(originalIcon.iconWidth, originalIcon.iconHeight)
    return if (iconSize == userScaledSize)
      originalIcon
    else
      IconUtil.scale(originalIcon, null, userScaledSize.toFloat() / iconSize)
  }
}

private class PngIconData(private val originalImage: Image?, userScaledSize: Int) : IconData(userScaledSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon {
    if (originalImage == null) {
      return emptyIcon()
    }
    val targetSize = if (UIUtil.isRetina()) 32 else (userScaledSize * pixScale).toInt()
    return IconUtil.toRetinaAwareIcon(
      Scalr.resize(ImageUtil.toBufferedImage(originalImage), Scalr.Method.ULTRA_QUALITY, targetSize),
      sysScale
    )
  }
}

private class EmptyIconData(userScaledSize: Int) : IconData(userScaledSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon = EmptyIcon.create(userScaledSize)
}

private object ProjectIconPalette : ColorPalette() {
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
