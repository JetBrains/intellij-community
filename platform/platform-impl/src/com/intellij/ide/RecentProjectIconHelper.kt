// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconDeferrer
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.loadRasterImage
import com.intellij.ui.icons.toRetinaAwareIcon
import com.intellij.ui.paint.withTxAndClipAligned
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.loadWithSizes
import com.intellij.util.IconUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.ui.*
import org.imgscalr.Scalr
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.SystemIndependent
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

internal fun unscaledProjectIconSize() = Registry.intValue("ide.project.icon.size", 20)

internal fun userScaledProjectIconSize() = JBUIScale.scale(unscaledProjectIconSize())

private const val IDEA_DIR = Project.DIRECTORY_STORE_FOLDER

private fun getDotIdeaPath(path: Path): Path {
  if (Files.isDirectory(path) || path.parent == null) {
    return ProjectStorePathManager.getInstance().getStoreDirectoryPath(path)
  }

  val fileName = path.fileName.toString()
  val dotIndex = fileName.lastIndexOf('.')
  val fileNameWithoutExt = if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)
  return path.parent.resolve("$IDEA_DIR/$IDEA_DIR.$fileNameWithoutExt/$IDEA_DIR")
}

private val projectIconCache = ContainerUtil.createSoftValueMap<Pair<String, Int>, ProjectIcon>()

private val cacheEpoch = AtomicInteger()

@Internal
class RecentProjectIconHelper {
  companion object {
    internal fun getDotIdeaPath(path: String): Path? {
      try {
        return getDotIdeaPath(Path.of(path))
      }
      catch (e: InvalidPathException) {
        return null
      }
    }

    internal fun createIcon(file: Path): Icon = ProjectFileIcon(loadIconFile(file))

    internal fun createIcon(file: Path, size: Int): Icon = ProjectFileIcon(loadIconFile(file, size))

    @Internal
    fun createIcon(data: ByteArray, svg: Boolean, size: Int): Icon = ProjectFileIcon(loadIcon(data, svg, size))

    fun createIcon(data: ByteArray, svg: Boolean): Icon = createIcon(data, svg, unscaledProjectIconSize())

    fun refreshProjectIcon(path: @SystemIndependent String) {
      cacheEpoch.incrementAndGet()
      projectIconCache.keys
        .filter { it.first == path }
        .forEach { projectIconCache.remove(it) }
    }

    fun getProjectName(path: @SystemIndependent String, recentProjectManager: RecentProjectsManagerBase): String {
      val displayName = recentProjectManager.getDisplayName(path)
      return when {
        displayName == null -> recentProjectManager.getProjectName(path)
        displayName.contains(',') -> iconTextForCommaSeparatedName(displayName)
        else -> displayName
      }
    }

    @JvmStatic
    fun generateProjectIcon(path: @SystemIndependent String,
                            isProjectValid: Boolean,
                            size: Int = unscaledProjectIconSize(),
                            projectName: String? = null): Icon {
      val generatedProjectIcon = generateProjectIcon(path, isProjectValid, size, null, projectName)

      projectIconCache.put(Pair(path, size), ProjectIcon(icon = generatedProjectIcon,
                                                         isProjectValid = isProjectValid,
                                                         lastUsedProjectIconSize = JBUIScale.scale(size)))

      return generatedProjectIcon
    }

    fun generateProjectIcon(path: @SystemIndependent String,
                            isProjectValid: Boolean,
                            size: Int = unscaledProjectIconSize(),
                            colorIndex: Int?,
                            projectName: String? = null): Icon {
      val name = projectName ?: getProjectName(path, RecentProjectsManagerBase.getInstanceEx())
      val palette = if (colorIndex != null) ChangeProjectIconPalette(colorIndex) else ProjectIconPalette

      var generatedProjectIcon: Icon = AvatarIcon(targetSize = size,
                                                  arcRatio = 0.4,
                                                  gradientSeed = path,
                                                  avatarName = name,
                                                  palette = palette).withIconPreScaled(false)

      if (!isProjectValid) {
        generatedProjectIcon = IconUtil.desaturate(generatedProjectIcon)
      }

      return generatedProjectIcon
    }

    // Examples:
    // - "First, Second" => "FS"
    // - "First Project, Second Project" => "FS"
    private fun iconTextForCommaSeparatedName(name: String): String {
      return name.split(',')
        .take(2)
        .map { word -> word.firstOrNull { !it.isWhitespace() } ?: "" }
        .joinToString("")
        .uppercase(Locale.getDefault())
    }
  }

  fun getProjectIcon(
    path: @SystemIndependent String,
    isProjectValid: Boolean = true,
    iconSize: Int = unscaledProjectIconSize(),
    name: String? = null,
  ): Icon {
    if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
      return EmptyIcon.create(iconSize)
    }

    return IconDeferrer.getInstance().defer(
      EmptyIcon.create(iconSize),
      LocalProjectIconKey(cacheEpoch.get(), path, isProjectValid, iconSize, name),
    ) {
      it.loadIcon()
    }
  }

  fun getNonLocalProjectIcon(
    id: String,
    isProjectValid: Boolean = true,
    iconSize: Int = unscaledProjectIconSize(),
    name: String? = null,
  ): Icon {
    return IconDeferrer.getInstance().defer(
      EmptyIcon.create(iconSize),
      NonLocalProjectIconKey(cacheEpoch.get(), id, isProjectValid, iconSize, name),
    ) {
      it.loadIcon()
    }
  }


  fun hasCustomIcon(project: Project): Boolean =
    ProjectWindowCustomizerService.projectPath(project)?.let { getCustomIconFileInfo(it) } != null
}

private sealed class DeferredIconKey {
  abstract fun loadIcon(): Icon
}

private data class LocalProjectIconKey(
  val cacheEpoch: Int,
  val path: @SystemIndependent String,
  val isProjectValid: Boolean,
  val iconSize: Int,
  val name: String?,
) : DeferredIconKey() {
  override fun loadIcon(): Icon =
    getCustomIcon(path, isProjectValid, iconSize)
    ?: getGeneratedProjectIcon(path, isProjectValid, iconSize, name)
}

private data class NonLocalProjectIconKey(
  val cacheEpoch: Int,
  val id: String,
  val isProjectValid: Boolean,
  val iconSize: Int,
  val name: String?,
) : DeferredIconKey() {
  override fun loadIcon(): Icon =
    getGeneratedProjectIcon(path = id, isProjectValid, iconSize, name)
}

private fun getCustomIconFileInfo(path: @SystemIndependent String): Pair<Path, BasicFileAttributes>? {
  val file = sequenceOf("icon.svg", "icon.png")
               .mapNotNull { RecentProjectIconHelper.getDotIdeaPath(path)?.resolve(it) }
               .firstOrNull { Files.exists(it) } ?: return null

  val fileInfo = file.basicAttributesIfExists() ?: return null

  return Pair(file, fileInfo)
}

private fun getCustomIcon(path: @SystemIndependent String, isProjectValid: Boolean, iconSize: Int): Icon? {
  val (file, fileInfo) = getCustomIconFileInfo(path) ?: return null
  val timestamp = fileInfo.lastModifiedTime().toMillis()

  var iconWrapper = projectIconCache.get(Pair(path, iconSize))
  if (iconWrapper != null && isCachedIcon(iconWrapper, isProjectValid, timestamp)) {
    return iconWrapper.icon
  }

  val borderWidth = 2
  var fileIcon = RecentProjectIconHelper.createIcon(file, iconSize - borderWidth * 2)
  if (!isProjectValid) {
    fileIcon = IconUtil.desaturate(fileIcon)
  }

  val icon = LayeredIcon(2).withIconPreScaled(false)
  icon.setIcon(EmptyIcon.create(iconSize), 0)
  icon.setIcon(fileIcon, 1, borderWidth, borderWidth)

  iconWrapper = ProjectIcon(icon = icon,
                            isProjectValid = isProjectValid,
                            lastUsedProjectIconSize = JBUIScale.scale(iconSize),
                            timestamp = timestamp)
  projectIconCache.put(Pair(path, iconSize), iconWrapper)
  return iconWrapper.icon
}

private fun getGeneratedProjectIcon(path: @SystemIndependent String,
                                    isProjectValid: Boolean,
                                    size: Int = unscaledProjectIconSize(),
                                    name: String? = null): Icon {
  val projectIcon = projectIconCache.get(Pair(path, size))
  if (projectIcon != null && isCachedIcon(projectIcon, isProjectValid, name = name)) {
    return projectIcon.icon
  }
  return RecentProjectIconHelper.generateProjectIcon(path, isProjectValid, size, projectName = name)
}

private fun isCachedIcon(icon: ProjectIcon, isProjectValid: Boolean, timestamp: Long? = null, name: String? = null): Boolean {
  val isNameChanged = (icon.icon as? AvatarIcon)?.avatarName?.let { it != name } ?: false
  val isTimestampChanged = timestamp?.let { icon.timestamp != it } ?: false

  return icon.isProjectValid == isProjectValid
         && icon.lastUsedProjectIconSize == userScaledProjectIconSize()
         && !isNameChanged && !isTimestampChanged
}

private data class ProjectIcon(
  @JvmField val icon: Icon,
  @JvmField val isProjectValid: Boolean,
  @JvmField val lastUsedProjectIconSize: Int,
  @JvmField val timestamp: Long? = null
)

@Internal
class ProjectFileIcon internal constructor(
  val iconData: IconData,
) : JBCachingScalableIcon<ProjectFileIcon>() {

  private var cachedIcon: Icon? = null
  private var cachedIconSysScale: Float? = null
  private var cachedIconPixScale: Float? = null

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    g as Graphics2D
    val sysScale = JBUIScale.sysScale(g)
    val pixScale = JBUI.pixScale(g.deviceConfiguration)
    val cachedIcon = this.cachedIcon
    val delegate: Icon
    if (cachedIcon != null &&
        cachedIconSysScale == sysScale &&
        cachedIconPixScale == pixScale) {
      delegate = cachedIcon
    }
    else {
      delegate = try {
        iconData.getScaledIcon(sysScale, pixScale)
      }
      catch (e: Throwable) {
        logger<IconData>().warn("Cannot render $iconData", e)
        createEmptyIcon(iconData.iconSize, pixScale)
      }

      this.cachedIcon = delegate
      cachedIconSysScale = sysScale
      cachedIconPixScale = pixScale
    }
    withTxAndClipAligned(g = g, x = x, y = y, width = delegate.iconWidth, height = delegate.iconHeight) { ag ->
      delegate.paintIcon(c, ag, 0, 0)
    }
  }

  override fun getIconWidth(): Int = JBUIScale.scale(iconData.iconSize)

  override fun getIconHeight(): Int = JBUIScale.scale(iconData.iconSize)

  override fun copy(): ProjectFileIcon = ProjectFileIcon(iconData)
}

private fun loadIconFile(file: Path, size: Int = unscaledProjectIconSize()): IconData {
  try {
    if (file.toString().endsWith(".svg", ignoreCase = true)) {
      return SvgIconDataImpl(file = file, iconSize = size)
    }
    else {
      return PngIconDataImpl(file = file, sourceImage = Files.newInputStream(file).use { loadRasterImage(it) }, iconSize = size)
    }
  }
  catch (e: Exception) {
    logger<RecentProjectIconHelper>().debug(e)
    return EmptyIconData(size)
  }
}

private fun loadIcon(data: ByteArray, svg: Boolean, size: Int = unscaledProjectIconSize()): IconData {
  if (svg) {
    return object : SvgIconData(size) {
      override fun getData(): ByteArray = data
    }
  }
  else {
    return object : PngIconData(data.inputStream().use { loadRasterImage(it) }, size) {
      override fun getData(): ByteArray = data
    }
  }
}

@Internal
sealed class IconData(val iconSize: Int = unscaledProjectIconSize()) {
  abstract fun getScaledIcon(sysScale: Float, pixScale: Float): Icon
}

@Internal
abstract class ImageIconData(iconSize: Int = unscaledProjectIconSize()) : IconData(iconSize) {
  abstract fun getData(): ByteArray
}

@get:Internal
val ImageIconData.isSvg: Boolean
  get() = this is SvgIconData

private abstract class SvgIconData(iconSize: Int = unscaledProjectIconSize()) : ImageIconData(iconSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon {
    val userSize = JBUIScale.scale(iconSize)
    return JBImageIcon(loadWithSizes(sizes = listOf(userSize), data = getData(), scale = sysScale).first())
  }
}

private abstract class PngIconData(private val sourceImage: BufferedImage,
                                   iconSize: Int = unscaledProjectIconSize()) : ImageIconData(iconSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon {
    val targetSize = ((iconSize * pixScale) + 0.5f).toInt()
    val image = Scalr.resize(sourceImage, Scalr.Method.ULTRA_QUALITY, targetSize)
    return toRetinaAwareIcon(image = image, sysScale = sysScale)
  }
}

private class SvgIconDataImpl(val file: Path, iconSize: Int = unscaledProjectIconSize()) : SvgIconData(iconSize) {
  override fun getData(): ByteArray = Files.readAllBytes(file)
  override fun toString() = "SvgIconDataImpl(file=$file, iconSize=$iconSize)"
}

private class PngIconDataImpl(val file: Path, sourceImage: BufferedImage,
                              iconSize: Int = unscaledProjectIconSize()) : PngIconData(sourceImage, iconSize) {
  override fun getData(): ByteArray = Files.readAllBytes(file)
}

private class EmptyIconData(iconSize: Int = unscaledProjectIconSize()) : IconData(iconSize) {
  override fun getScaledIcon(sysScale: Float, pixScale: Float): Icon {
    return createEmptyIcon(iconSize, pixScale)
  }
}

private fun createEmptyIcon(iconSize: Int, pixScale: Float) = EmptyIcon.create(((iconSize * pixScale) + 0.5f).toInt())

@Internal
object ProjectIconPalette : ColorPalette {
  @Suppress("UnregisteredNamedColor")
  override val gradients: Array<Pair<Color, Color>>
    get() {
      return arrayOf(
        JBColor.namedColor("RecentProject.Color1.Avatar.Start", JBColor(0xDB3D3C, 0xCE443C))
          to JBColor.namedColor("RecentProject.Color1.Avatar.End", JBColor(0xFF8E42, 0xE77E41)),
        JBColor.namedColor("RecentProject.Color2.Avatar.Start", JBColor(0xF57236, 0xE27237))
          to JBColor.namedColor("RecentProject.Color2.Avatar.End", JBColor(0xFCBA3F, 0xE8A83E)),
        JBColor.namedColor("RecentProject.Color3.Avatar.Start", JBColor(0x2BC8BB, 0x2DBCAD))
          to JBColor.namedColor("RecentProject.Color3.Avatar.End", JBColor(0x36EBAE, 0x35D6A4)),
        JBColor.namedColor("RecentProject.Color4.Avatar.Start", JBColor(0x359AF2, 0x3895E1))
          to JBColor.namedColor("RecentProject.Color4.Avatar.End", JBColor(0x57DBFF, 0x51C5EA)),
        JBColor.namedColor("RecentProject.Color5.Avatar.Start", JBColor(0x8379FB, 0x7B75E8))
          to JBColor.namedColor("RecentProject.Color5.Avatar.End", JBColor(0x85A8FF, 0x7D99EB)),
        JBColor.namedColor("RecentProject.Color6.Avatar.Start", JBColor(0x7E54B5, 0x7854AD))
          to JBColor.namedColor("RecentProject.Color6.Avatar.End", JBColor(0x9486FF, 0x897AE6)),
        JBColor.namedColor("RecentProject.Color7.Avatar.Start", JBColor(0xD63CC8, 0x8F4593))
          to JBColor.namedColor("RecentProject.Color7.Avatar.End", JBColor(0xF582B9, 0xB572E3)),
        JBColor.namedColor("RecentProject.Color8.Avatar.Start", JBColor(0x954294, 0xC840B9))
          to JBColor.namedColor("RecentProject.Color8.Avatar.End", JBColor(0xC87DFF, 0xE074AE)),
        JBColor.namedColor("RecentProject.Color9.Avatar.Start", JBColor(0xE75371, 0xD75370))
          to JBColor.namedColor("RecentProject.Color9.Avatar.End", JBColor(0xFF78B5, 0xE96FA3))
      )
    }

  override fun gradient(seed: String?): Pair<Color, Color> {
    seed ?: return gradients[0]
    return ProjectWindowCustomizerService.getInstance().getRecentProjectIconColor(seed)
  }
}

class ChangeProjectIconPalette(val index: Int) : ColorPalette {
  override val gradients: Array<Pair<Color, Color>>
    get() = ProjectIconPalette.gradients

  override fun gradient(seed: String?): Pair<Color, Color> {
    return gradients[index]
  }
}
