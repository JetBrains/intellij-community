// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconPatcher
import com.intellij.ide.FileIconProvider
import com.intellij.ide.TypePresentationService
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.fileTypes.DirectoryFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.IconLoader.filterIcon
import com.intellij.openapi.util.IconLoader.getIcon
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.ui.ColorUtil
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.CopyableIcon
import com.intellij.ui.icons.TextIcon
import com.intellij.ui.icons.copyIcon
import com.intellij.ui.scale.JBUIScale.getFontScale
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil.ICON_FLAG_IGNORE_MASK
import com.intellij.util.SVGLoader.paintIconWithSelection
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.RGBImageFilter
import java.util.*
import java.util.function.Supplier
import java.util.function.ToIntFunction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private val PROJECT_WAS_EVER_INITIALIZED = Key.create<Boolean>("iconDeferrer:projectWasEverInitialized")

private fun wasEverInitialized(project: Project): Boolean {
  var was = project.getUserData(PROJECT_WAS_EVER_INITIALIZED)
  if (was == null) {
    if (project.isInitialized) {
      was = true
      project.putUserData(PROJECT_WAS_EVER_INITIALIZED, true)
    }
    else {
      was = false
    }
  }
  return was
}

private val ICON_NULLABLE_FUNCTION = { key: FileIconKey ->
  IconUtil.computeFileIcon(file = key.file, flags = key.flags, project = key.project)
}

private val toolbarDecoratorIconsFolder: @NonNls String
  get() = "toolbarDecorator/${if (SystemInfoRt.isMac) "mac/" else ""}"

object IconUtil {
  val ICON_FLAG_IGNORE_MASK: Key<Int> = Key<Int>("ICON_FLAG_IGNORE_MASK")

  @JvmStatic
  fun cropIcon(icon: Icon, maxWidth: Int, maxHeight: Int): Icon {
    @Suppress("NAME_SHADOWING") var maxWidth = maxWidth
    @Suppress("NAME_SHADOWING") var maxHeight = maxHeight
    if (icon.iconHeight <= maxHeight && icon.iconWidth <= maxWidth) {
      return icon
    }

    var image = IconLoader.toImage(icon, null) ?: return icon
    var scale = 1.0
    if (image is JBHiDPIScaledImage) {
      val hdpi = image
      scale = hdpi.scale
      hdpi.delegate?.let {
        image = it
      }
    }

    val bi = ImageUtil.toBufferedImage(image)
    val g = bi.createGraphics()
    val imageWidth = ImageUtil.getRealWidth(image)
    val imageHeight = ImageUtil.getRealHeight(image)
    maxWidth = if (maxWidth == Int.MAX_VALUE) Int.MAX_VALUE else (maxWidth * scale).roundToLong().toInt()
    maxHeight = if (maxHeight == Int.MAX_VALUE) Int.MAX_VALUE else (maxHeight * scale).roundToLong().toInt()
    val w = min(imageWidth, maxWidth)
    val h = min(imageHeight, maxHeight)
    val img = ImageUtil.createImage(g, w, h, Transparency.TRANSLUCENT)
    val offX = if (imageWidth > maxWidth) (imageWidth - maxWidth) / 2 else 0
    val offY = if (imageHeight > maxHeight) (imageHeight - maxHeight) / 2 else 0
    for (col in 0 until w) {
      for (row in 0 until h) {
        img.setRGB(col, row, bi.getRGB(col + offX, row + offY))
      }
    }
    g.dispose()
    return JBImageIcon(RetinaImage.createFrom(img, scale, null))
  }

  @JvmStatic
  fun cropIcon(icon: Icon, area: Rectangle): Icon {
    return if (Rectangle(icon.iconWidth, icon.iconHeight).contains(area)) CropIcon(icon, area) else icon
  }

  @JvmStatic
  fun flip(icon: Icon, horizontal: Boolean): Icon {
    return object : Icon {
      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g.create() as Graphics2D
        try {
          val transform = AffineTransform.getTranslateInstance((if (horizontal) x + iconWidth else x).toDouble(),
                                                               (if (horizontal) y else y + iconHeight).toDouble())
          transform.concatenate(AffineTransform.getScaleInstance((if (horizontal) -1 else 1).toDouble(), (if (horizontal) 1 else -1).toDouble()))
          transform.preConcatenate(g2d.transform)
          g2d.transform = transform
          icon.paintIcon(c, g2d, 0, 0)
        }
        finally {
          g2d.dispose()
        }
      }

      override fun getIconWidth(): Int = icon.iconWidth

      override fun getIconHeight(): Int = icon.iconHeight

      override fun toString(): String = "IconUtil.flip for $icon"
    }
  }

  /**
   * @return a deferred icon for the file, taking into account [FileIconProvider] and [FileIconPatcher] extensions.
   */
  @JvmStatic
  fun computeFileIcon(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon {
    return computeFileIconImpl(BackedVirtualFile.getOriginFileIfBacked(file), project, flags)
  }

  private fun computeFileIconImpl(file: VirtualFile, project: Project?, flags: Int): Icon {
    if (!file.isValid || project != null && (project.isDisposed || !wasEverInitialized(project))) {
      return AllIcons.FileTypes.Unknown
    }

    @Suppress("NAME_SHADOWING") val flags = filterFileIconFlags(file, flags)
    val providerIcon = getProviderIcon(file, flags, project)
    var icon = providerIcon ?: computeFileTypeIcon(file, false)
    val dumb = project != null && DumbService.getInstance(project).isDumb
    for (patcher in FileIconPatcher.EP_NAME.extensionList) {
      if (dumb && !DumbService.isDumbAware(patcher)) {
        continue
      }

      // render without a locked icon patch since we are going to apply it later anyway
      icon = patcher.patchIcon(icon, file, flags and Iconable.ICON_FLAG_READ_STATUS.inv(), project)
    }
    if (file.`is`(VFileProperty.SYMLINK)) {
      icon = LayeredIcon.layeredIcon(arrayOf(icon, PlatformIcons.SYMLINK_ICON))
    }
    if (BitUtil.isSet(flags, Iconable.ICON_FLAG_READ_STATUS) &&
        Registry.`is`("ide.locked.icon.enabled", false) &&
        (!file.isWritable || !WritingAccessProvider.isPotentiallyWritable(file, project))) {
      icon = LayeredIcon.layeredIcon(arrayOf(icon, PlatformIcons.LOCKED_ICON))
    }
    LastComputedIconCache.put(file, icon, flags)
    return icon
  }

  /**
   * @return a deferred icon for the file, taking into account [FileIconProvider] and [FileIconPatcher] extensions.
   * Use [computeFileIcon] where possible (e.g., in background threads) to get a non-deferred icon.
   */
  @JvmStatic
  fun getIcon(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon {
    return getIconImpl(BackedVirtualFile.getOriginFileIfBacked(file), flags, project)
  }

  private fun getIconImpl(file: VirtualFile, flags: Int, project: Project?): Icon {
    val lastIcon = LastComputedIconCache.get(file, flags)
    val base = lastIcon ?: computeBaseFileIcon(file)
    return IconManager.getInstance().createDeferredIcon(base, FileIconKey(file, project, flags), ICON_NULLABLE_FUNCTION)
  }

  /**
   * @return an icon for a file that's quick to calculate, most likely based on the file type
   * @see [computeFileIcon]
   * @see [com.intellij.openapi.fileTypes.FileType.getIcon]
   */
  @JvmStatic
  fun computeBaseFileIcon(vFile: VirtualFile): Icon = computeFileTypeIcon(vFile, true)

  private fun computeFileTypeIcon(vFile: VirtualFile, onlyFastChecks: Boolean): Icon {
    var icon = TypePresentationService.getService().getIcon(vFile)
    if (icon != null) {
      return icon
    }
    val fileType = if (onlyFastChecks) FileTypeRegistry.getInstance().getFileTypeByFileName(vFile.name) else vFile.fileType
    if (vFile.isDirectory && fileType !is DirectoryFileType) {
      return IconManager.getInstance().tooltipOnlyIfComposite(PlatformIcons.FOLDER_ICON)
    }
    icon = fileType.icon
    return icon ?: getEmptyIcon(false)
  }

  private fun getProviderIcon(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon? {
    return FileIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getIcon(file, flags, project) }
  }

  @JvmStatic
  fun getEmptyIcon(showVisibility: Boolean): Icon {
    val baseIcon = RowIcon(2)
    baseIcon.setIcon(EmptyIcon.create(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class)), 0)
    if (showVisibility) {
      baseIcon.setIcon(EmptyIcon.create(PlatformIcons.PUBLIC_ICON), 1)
    }
    return baseIcon
  }

  @JvmOverloads
  @JvmStatic
  @Suppress("UndesirableClassUsage")
  fun toImage(icon: Icon, context: ScaleContext? = null): Image {
    return IconLoader.toImage(icon = icon, scaleContext = context) ?: BufferedImage(1, 0, BufferedImage.TYPE_INT_ARGB)
  }

  @JvmOverloads
  @JvmStatic
  fun toBufferedImage(icon: Icon, inUserScale: Boolean = false): BufferedImage = toBufferedImage(icon, null, inUserScale)

  @JvmStatic
  @Suppress("UndesirableClassUsage")
  fun toBufferedImage(icon: Icon, context: ScaleContext?, inUserScale: Boolean): BufferedImage {
    var image = IconLoader.toImage(icon, context)
    if (image == null) {
      image = BufferedImage(1, 0, BufferedImage.TYPE_INT_ARGB)
    }
    return ImageUtil.toBufferedImage(image, inUserScale)
  }

  @JvmStatic
  val addIcon: Icon
    get() = AllIcons.General.Add
  @JvmStatic
  val removeIcon: Icon
    get() = AllIcons.General.Remove

  @JvmStatic
  val moveUpIcon: Icon
    get() = AllIcons.Actions.MoveUp
  @JvmStatic
  val moveDownIcon: Icon
    get() = AllIcons.Actions.MoveDown
  @JvmStatic
  val editIcon: Icon
    get() = AllIcons.Actions.Edit
  @JvmStatic
  val addClassIcon: Icon
    get() = AllIcons.ToolbarDecorator.AddClass
  @JvmStatic
  val addPatternIcon: Icon
    get() = AllIcons.ToolbarDecorator.AddPattern

  @Suppress("unused")
  @JvmStatic
  val addBlankLineIcon: Icon
    get() = AllIcons.ToolbarDecorator.AddBlankLine
  @Suppress("unused")
  @JvmStatic
  val addPackageIcon: Icon
    get() = AllIcons.ToolbarDecorator.AddFolder
  @JvmStatic
  val addLinkIcon: Icon
    get() = AllIcons.ToolbarDecorator.AddLink

  @Suppress("unused")
  @JvmStatic
  @get:Deprecated("This icon is not used by platform anymore.")
  val analyzeIcon: Icon
    get() = getIcon(toolbarDecoratorIconsFolder + "analyze.png", IconUtil::class.java.classLoader)

  @JvmStatic
  @Suppress("unused")
  fun paintInCenterOf(c: Component, g: Graphics, icon: Icon) {
    val x = (c.width - icon.iconWidth) / 2
    val y = (c.height - icon.iconHeight) / 2
    icon.paintIcon(c, g, x, y)
  }

  @JvmStatic
  fun toSize(icon: Icon?, width: Int, height: Int): Icon = IconSizeWrapper(icon, width, height)

  @JvmStatic
  fun paintSelectionAwareIcon(icon: Icon, component: JComponent?, g: Graphics, x: Int, y: Int, selected: Boolean) {
    if (selected) {
      paintIconWithSelection(icon = icon, c = component, g = g, x = x, y = y)
    }
    else {
      icon.paintIcon(component, g, x, y)
    }
  }

  /**
   * Use it only for icons under selection.
   */
  @ApiStatus.Internal
  @Contract("null -> null; !null -> !null")
  @JvmStatic
  fun wrapToSelectionAwareIcon(iconUnderSelection: Icon?): Icon? {
    if (iconUnderSelection == null) {
      return null
    }

    return object : Icon {
      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        paintIconWithSelection(icon = iconUnderSelection, c = c, g = g, x = x, y = y)
      }

      override fun getIconWidth(): Int = iconUnderSelection.iconWidth

      override fun getIconHeight(): Int = iconUnderSelection.iconHeight

      override fun toString(): String = "IconUtil.wrapToSelectionAwareIcon for $iconUnderSelection"
    }
  }

  @Deprecated("use {@link #scale(Icon, Component, float)}")
  @JvmStatic
  fun scale(source: Icon, scale: Double): Icon {
    return object : Icon {
      private val clampedScale = clampScale(scale)

      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        paintScaled(c = c, g = g, x = x, y = y, scale = clampedScale, source = source)
      }

      override fun getIconWidth(): Int = (source.iconWidth * clampedScale).toInt()
      override fun getIconHeight(): Int = (source.iconHeight * clampedScale).toInt()
      override fun toString(): String = "IconUtil.scale for $source"
    }
  }

  @JvmStatic
  fun resizeSquared(source: Icon, size: Int): Icon {
    return object : Icon {
      private val sizeValue = JBUI.uiIntValue("ResizedIcon", size)

      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val scale = clampScale(sizeValue.get().toDouble() / source.iconWidth.toDouble())
        paintScaled(c = c, g = g, x = x, y = y, scale = scale, source = source)
      }

      override fun getIconWidth(): Int = sizeValue.get()
      override fun getIconHeight(): Int = sizeValue.get()
      override fun toString(): String = "IconUtil.resizeSquared for $source"
    }
  }

  /**
   * Returns a copy of the provided icon.
   * @see CopyableIcon
   */
  @JvmStatic
  fun copy(icon: Icon, ancestor: Component?): Icon = copyIcon(icon = icon, ancestor = ancestor, deepCopy = false)

  /**
   * Returns a deep copy of the provided icon.
   * @see CopyableIcon
   */
  @JvmStatic
  fun deepCopy(icon: Icon, ancestor: Component?): Icon = copyIcon(icon = icon, ancestor = ancestor, deepCopy = true)

  /**
   * Returns a scaled icon instance.
   *
   * The method delegates to [ScalableIcon.scale] when applicable, otherwise defaults to [scale].
   *
   * In the following example:
   * ```
   * Icon myIcon = new MyIcon();
   * Icon scaledIcon = IconUtil.scale(myIcon, myComp, 2f);
   * Icon anotherScaledIcon = IconUtil.scale(scaledIcon, myComp, 2f);
   * assert(scaledIcon.getIconWidth() == anotherScaledIcon.getIconWidth()); // compare the scale of the icons
   * ```
   * ... the result of the assertion depends on `MyIcon` implementation.
   * When `scaledIcon` is an instance of [ScalableIcon], then `anotherScaledIcon` should be scaled according to the [ScalableIcon] docs,
   * and the assertion should pass.
   * Otherwise, `anotherScaledIcon` should be 2 times bigger than `scaledIcon`, and 4 times bigger than `myIcon`.
   * So, prior to scaling the icon recursively, the returned icon should be inspected for its type to understand the result.
   * But recursive scaling should better be avoided.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param scale the scale factor
   * @return the scaled icon
   */
  @JvmStatic
  fun scale(icon: Icon, ancestor: Component?, scale: Float): Icon {
    if (icon is CachedImageIcon) {
      return icon.scale(scale = scale, ancestor = ancestor)
    }

    val ctx = if (ancestor == null && icon is ScaleContextAware) {
      // in this case, the icon's context should be preserved, except the OBJ_SCALE
      val usrCtx = icon.scaleContext
      ScaleContext.create(usrCtx)
    }
    else {
      ScaleContext.create(ancestor)
    }
    ctx.setScale(ScaleType.OBJ_SCALE.of(scale))
    return scale(icon = icon, scaleContext = ctx)
  }

  /**
   * Returns a scaled icon instance.
   *
   * The passed `ctx` is applied to the icon and the [ScaleType.OBJ_SCALE] is used to scale it.
   *
   * @see .scale
   * @param icon the icon to scale
   * @param scaleContext the scale context to apply
   * @return the scaled icon
   */
  @JvmStatic
  fun scale(icon: Icon, scaleContext: ScaleContext): Icon {
    val scale = scaleContext.getScale(ScaleType.OBJ_SCALE)
    if (icon !is CopyableIcon) {
      @Suppress("DEPRECATION")
      return scale(source = icon, scale = scale)
    }

    val copiedIcon = icon.deepCopy()
    if (copiedIcon !is ScalableIcon) {
      @Suppress("DEPRECATION")
      return scale(source = copiedIcon, scale = scale)
    }

    if (copiedIcon is ScaleContextAware) {
      val newScaleContext = scaleContext.copy<ScaleContext>()
      // reset OBJ_SCALE in the context to preserve ScalableIcon.scale(float) implementation
      // from accumulation of the scales: OBJ_SCALE * scale.
      newScaleContext.setScale(ScaleType.OBJ_SCALE.of(1f))
      copiedIcon.updateScaleContext(newScaleContext)
    }
    return copiedIcon.scale(scale.toFloat())
  }

  /**
   * Returns a scaled icon instance, in a scale of the provided font size.
   *
   * The method delegates to [ScalableIcon.scale] when applicable,
   * otherwise defaults to [.scale]
   *
   * Refer to [.scale] for more details.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param fontSize reference font size
   * @return the scaled icon
   */
  @JvmStatic
  fun scaleByFont(icon: Icon, ancestor: Component?, fontSize: Float): Icon {
    var scale = getFontScale(fontSize)
    if (icon is ScaleContextAware) {
      val ctxIcon = icon as ScaleContextAware
      // take into account the user scale of the icon
      val usrScale = ctxIcon.scaleContext.getScale(ScaleType.USR_SCALE)
      scale /= usrScale.toFloat()
    }
    return scale(icon = icon, ancestor = ancestor, scale = scale)
  }

  @JvmStatic
  fun scaleByIconWidth(icon: Icon?, ancestor: Component?, defaultIcon: Icon): Icon {
    return scaleByIcon(icon, ancestor, defaultIcon) { it.iconWidth }
  }

  @JvmOverloads
  @JvmStatic
  fun colorize(source: Icon, color: Color, keepGray: Boolean = false): Icon {
    return filterIcon(icon = source, filterSupplier = { ColorFilter(color, keepGray) })
  }

  @JvmOverloads
  @JvmStatic
  fun colorize(g: Graphics2D?, source: Icon, color: Color, keepGray: Boolean = false): Icon {
    return filterIcon(g = g, source = source, filter = ColorFilter(color, keepGray))
  }

  @JvmStatic
  fun desaturate(source: Icon): Icon {
    return filterIcon(icon = source, filterSupplier = { DesaturationFilter() })
  }

  @JvmStatic
  fun brighter(source: Icon, tones: Int): Icon = filterIcon(icon = source, filterSupplier = { BrighterFilter(tones) })

  @JvmStatic
  fun darker(source: Icon, tones: Int): Icon = filterIcon(icon = source, filterSupplier = { DarkerFilter(tones) })

  @JvmStatic
  fun createImageIcon(img: Image): JBImageIcon {
    return object : JBImageIcon(img) {
      override fun getIconWidth(): Int = ImageUtil.getUserWidth(image)

      override fun getIconHeight(): Int = ImageUtil.getUserHeight(image)
    }
  }

  @JvmStatic
  fun textToIcon(text: String, component: Component, fontSize: Float): Icon = TextIcon(text, component, fontSize)

  @JvmStatic
  fun addText(base: Icon, text: String): Icon {
    val icon = LayeredIcon(2)
    icon.setIcon(base, 0)
    icon.setIcon(textToIcon(text, JLabel(), scale(6.0f)), 1, SwingConstants.SOUTH_EAST)
    return icon
  }

  @JvmStatic
  @Deprecated("Please use `IconLoader.filterIcon` instead", replaceWith = ReplaceWith("IconLoader.filterIcon", "com.intellij.openapi.util.IconLoader"))
  fun filterIcon(icon: Icon, filterSupplier: Supplier<out RGBImageFilter>, @Suppress("UNUSED_PARAMETER") ancestor: Component?): Icon {
    return filterIcon(icon = icon, filterSupplier = filterSupplier::get)
  }

  /**
   * This method works with compound icons like RowIcon or LayeredIcon
   * and replaces its inner 'simple' icon with another one recursively
   * @return original icon with modified inner state
   */
  @JvmStatic
  fun replaceInnerIcon(icon: Icon?, toCheck: Icon, toReplace: Icon): Icon? {
    if (icon is LayeredIcon) {
      val layers = icon.allLayers
      for (i in layers.indices) {
        val layer = layers[i]
        if (layer === toCheck) {
          layers[i] = toReplace
        }
        else {
          replaceInnerIcon(icon = layer, toCheck = toCheck, toReplace = toReplace)
        }
      }
    }
    else if (icon is RowIcon) {
      val allIcons = icon.allIcons
      for ((i, anIcon) in allIcons.withIndex()) {
        if (anIcon === toCheck) {
          icon.setIcon(toReplace, i)
        }
        else {
          replaceInnerIcon(icon = anIcon, toCheck = toCheck, toReplace = toReplace)
        }
      }
    }
    return icon
  }

  @JvmStatic
  fun rowIcon(left: Icon?, right: Icon?): Icon? {
    return if (left != null && right != null) RowIcon(left, right) else left ?: right
  }
}

private class IconSizeWrapper(private val icon: Icon?, private val width: Int, private val height: Int) : Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    paintIcon(icon = icon, c = c, g = g, x = x, y = y)
  }

  private fun paintIcon(icon: Icon?, c: Component?, g: Graphics?, x: Int, y: Int) {
    if (icon == null) {
      return
    }

    icon.paintIcon(c, g, x + (width - icon.iconWidth) / 2, y + (height - icon.iconHeight) / 2)
  }

  override fun getIconWidth(): Int = width

  override fun getIconHeight(): Int = height
}

class CropIcon internal constructor(val sourceIcon: Icon, val crop: Rectangle) : Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val customG = g.create()
    try {
      val iconClip = Rectangle(x, y, crop.width, crop.height)
      val gClip = customG.clipBounds
      if (gClip != null) {
        Rectangle2D.intersect(iconClip, gClip, iconClip)
      }
      customG.clip = iconClip
      sourceIcon.paintIcon(c, customG, x - crop.x, y - crop.y)
    }
    finally {
      customG.dispose()
    }
  }

  override fun toString(): String = "${javaClass.simpleName} ($sourceIcon -> $crop)"
  override fun getIconWidth(): Int = crop.width
  override fun getIconHeight(): Int = crop.height
  override fun equals(other: Any?): Boolean = this === other || other is CropIcon && sourceIcon == other.sourceIcon && crop == other.crop
  override fun hashCode(): Int = Objects.hash(sourceIcon, crop)
}

private class ColorFilter(color: Color, private val keepGray: Boolean) : RGBImageFilter() {
  private val base = Color.RGBtoHSB(color.red, color.green, color.blue, null)

  override fun filterRGB(x: Int, y: Int, rgba: Int): Int {
    val r = rgba shr 16 and 0xff
    val g = rgba shr 8 and 0xff
    val b = rgba and 0xff
    val hsb = FloatArray(3)
    Color.RGBtoHSB(r, g, b, hsb)
    val rgb = Color.HSBtoRGB(base[0], base[1] * if (keepGray) hsb[1] else 1.0f, base[2] * hsb[2])
    return rgba and -0x1000000 or (rgb and 0xffffff)
  }
}

private class DesaturationFilter : RGBImageFilter() {
  override fun filterRGB(x: Int, y: Int, rgba: Int): Int {
    val r = rgba shr 16 and 0xff
    val g = rgba shr 8 and 0xff
    val b = rgba and 0xff
    val min = min(min(r, g), b)
    val max = max(r.coerceAtLeast(g), b)
    val grey = (max + min) / 2
    return rgba and -0x1000000 or (grey shl 16) or (grey shl 8) or grey
  }
}

private class BrighterFilter(private val tones: Int) : RGBImageFilter() {
  @Suppress("UseJBColor")
  override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
    val originalColor = Color(rgb, true)
    return ColorUtil.toAlpha(ColorUtil.brighter(originalColor, tones), originalColor.alpha).rgb
  }
}

private class DarkerFilter(private val tones: Int) : RGBImageFilter() {
  @Suppress("UseJBColor")
  override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
    val originalColor = Color(rgb, true)
    return ColorUtil.toAlpha(ColorUtil.darker(originalColor, tones), originalColor.alpha).rgb
  }
}

@IconFlags
private fun filterFileIconFlags(file: VirtualFile, @IconFlags flags: Int): Int {
  val fileTypeDataHolder = file.fileType as? UserDataHolder
  val fileTypeFlagIgnoreMask = ICON_FLAG_IGNORE_MASK.get(fileTypeDataHolder, 0)
  val flagIgnoreMask = ICON_FLAG_IGNORE_MASK.get(file, fileTypeFlagIgnoreMask)
  return flags and flagIgnoreMask.inv()
}

private fun clampScale(scale: Double): Double = scale.coerceIn(0.1, 32.0)

private fun paintScaled(c: Component?, g: Graphics, x: Int, y: Int, scale: Double, source: Icon) {
  val g2d = g.create() as Graphics2D
  try {
    g2d.translate(x, y)
    val transform = AffineTransform.getScaleInstance(scale, scale)
    transform.preConcatenate(g2d.transform)
    g2d.transform = transform
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    source.paintIcon(c, g2d, 0, 0)
  }
  finally {
    g2d.dispose()
  }
}

private fun scaleByIcon(icon: Icon?, ancestor: Component?, defaultIcon: Icon, size: ToIntFunction<in Icon>): Icon {
  if (icon == null || icon === defaultIcon) {
    return defaultIcon
  }

  val actual = size.applyAsInt(icon)
  val expected = size.applyAsInt(defaultIcon)
  return if (expected == actual) {
    icon
  }
  else {
    IconUtil.scale(icon = icon, ancestor = ancestor, scale = expected.toFloat() / actual)
  }
}

private fun filterIcon(g: Graphics2D?, source: Icon, filter: ColorFilter): Icon {
  val src = if (g == null) {
    ImageUtil.createImage(source.iconWidth, source.iconHeight, BufferedImage.TYPE_INT_ARGB)
  }
  else {
    ImageUtil.createImage(g, source.iconWidth, source.iconHeight, BufferedImage.TYPE_INT_ARGB)
  }
  val g2d = src.createGraphics()
  source.paintIcon(null, g2d, 0, 0)
  g2d.dispose()
  val image = if (g != null) {
    ImageUtil.createImage(g, source.iconWidth, source.iconHeight, BufferedImage.TYPE_INT_ARGB)
  }
  else {
    ImageUtil.createImage(source.iconWidth, source.iconHeight, BufferedImage.TYPE_INT_ARGB)
  }
  var rgba: Int
  for (y in 0 until src.raster.height) {
    for (x in 0 until src.raster.width) {
      rgba = src.getRGB(x, y)
      if (rgba and -0x1000000 != 0) {
        image.setRGB(x, y, filter.filterRGB(x, y, rgba))
      }
    }
  }
  return IconUtil.createImageIcon(image as Image)
}