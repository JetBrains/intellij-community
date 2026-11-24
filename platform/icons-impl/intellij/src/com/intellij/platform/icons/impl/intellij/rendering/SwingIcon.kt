// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.impl.intellij.debug.InspectableSwingIcon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.modifiers.ApplyableIconModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.ForceDarkVariantModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.ui.icons.StrokeIconProvider
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.SwingPaintInfo
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.icons.DarkIconProvider
import com.intellij.ui.icons.MenuBarIconProvider
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.MultiResolutionImageProvider
import javax.swing.ImageIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.WeakHashMap
import java.lang.ref.SoftReference
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * A Swing [Icon] that renders [iconDescriptor].
 *
 * [dark] is the explicit dark state of the icon: `true` for the dark artwork, `false` for the light artwork. `null`
 * follows the theme. It is an explicit dark state of the rendering context, so a dark state set inside [iconDescriptor]
 * wins over it. Only a dark state that [iconDescriptor] sets on another descriptor as a whole (see [withDarkState]) is
 * replaced. The [IconLoader.getDarkIcon] protocol ([DarkIconProvider]) sets [dark].
 */
internal class SwingIcon(
  override val iconDescriptor: IconDescriptor,
  scale: IconScale? = null,
  override val dark: Boolean? = null,
): Icon, ScalableSwingIcon, ScalableIcon, StrokeIconProvider, DarkIconProvider, MenuBarIconProvider, InspectableSwingIcon {
  /**
   * The identity factor scale ([IconScale.Default] == `factor(1)`) carries no size change, so normalize it to `null`.
   * This lets [com.intellij.platform.icons.impl.intellij.IntelliJIconManager.toIconDescriptor] unwrap back to the native
   * [iconDescriptor] instead of wrapping it in a scale layer. Without it, the Presentation write-bridge fails to
   * recognize its own icon and re-wraps the descriptor on every update cycle - each pass nesting another IconIconLayer +
   * identity ScaleModifier - which grows the descriptor without bound and buries the native image so the toolbar look's
   * enlargement never reaches a clean descriptor.
   */
  override val iconScale: IconScale? = scale?.takeUnless { it is FactorScale && it.factor == 1.0 }

  override fun withIconDescriptor(descriptor: IconDescriptor): Icon = SwingIcon(descriptor, iconScale, dark)

  internal val users = WeakHashMap<Component, Boolean>()

  /**
   * The icons of the last [SCALED_VARIANT_LIMIT] factors of [scale], which runs on the paint path, for example when the main
   * toolbar enlarges its icons to 20px on each repaint. A repaint then reuses the renderer. Like the scale cache of
   * `CachedImageIcon`, the cache is bounded and holds its icons by soft references.
   */
  private val scaledVariants = object : LinkedHashMap<Float, SoftReference<SwingIcon>>(SCALED_VARIANT_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Float, SoftReference<SwingIcon>>): Boolean =
      size > SCALED_VARIANT_LIMIT
  }

  // [getDarkIcon] runs on the paint path too (e.g. the main toolbar on a dark header on every repaint).
  @Volatile
  private var darkVariant: SwingIcon? = null

  @Volatile
  private var lightVariant: SwingIcon? = null

  /** The icon that [getDarkIcon] made this icon from, so that the variants of one icon are not nested. */
  private var darkOrigin: SwingIcon? = null

  private val renderingContext: RenderingContext by lazy {
    val flow = IconRendererManager.createUpdateFlow(null) {
      // an update can change the size, for example when a deferred icon is resolved
      measured = null
      withContext(Dispatchers.EDT) {
        for (user in users.keys) {
          user.repaint()
        }
      }
    }
    IconRendererManager.createRenderingContext(flow, dark?.let { DefaultImageModifiers(forcedDark = it) })
  }

  private val renderer by lazy {
    // [dark] replaces a dark state that the descriptor only sets on another descriptor, as in [getDarkIcon]
    val descriptor = if (dark != null) iconDescriptor.withoutDarkState() else iconDescriptor
    IconRendererManager.getInstance().createRenderer(descriptor, renderingContext)
  }

  /**
   * The size of the last measure, at its IDE zoom and theme digest. It is measured again after a zoom change, and after a
   * theme change, which can change the size, for example when a theme patches the path of an icon.
   */
  @Volatile
  private var measured: Measured? = null

  private class Measured(val zoom: Float, val themeDigest: String, val dimensions: Dimensions)

  private val dimensions: Dimensions
    get() {
      val zoom = ideZoom()
      val themeDigest = renderingContext.theme.digest()
      measured?.let { if (it.zoom == zoom && it.themeDigest == themeDigest) return it.dimensions }
      val dimensions = renderer.resolve(1f, iconScale, zoom).finalDimensions
      measured = Measured(zoom, themeDigest, dimensions)
      return dimensions
    }

  override fun scaled(scale: IconScale): ScalableSwingIcon {
    val mergedScale = if (this.iconScale != null) {
      when (scale) {
        is FactorScale if this.iconScale is FactorScale -> {
          factor(scale.factor * this.iconScale.factor)
        }
        // a limited fit cannot absorb the factor (a 16px icon in a 2x larger "at most" area stays 16px): fit, then scale
        is FactorScale if this.iconScale is FitAreaScale && (!this.iconScale.upscale || !this.iconScale.downscale) -> {
          val fit = this.iconScale
          return SwingIcon(iconDescriptor { icon(this@SwingIcon.iconDescriptor, IconModifier.scale(fit)) }, scale, dark)
        }
        is FactorScale if this.iconScale is FitAreaScale -> {
          fitArea(this.iconScale.width * scale.factor, this.iconScale.height * scale.factor, this.iconScale.relative)
        }
        else -> {
          scale
        }
      }
    } else {
      scale
    }
    return SwingIcon(iconDescriptor, mergedScale, dark)
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (c != null) {
      users.putIfAbsent(c, true)
    }
    val scaling = renderer.resolve(g.getDensity(), iconScale, ideZoom())
    withLayer(scaling, g, x, y) { newGraphics ->
      val swingApi = SwingLayerPaintingContext(
        c,
        newGraphics,
        0,
        0,
        scaling = scaling.context
      )
      if (IconRenderDebugger.isActive) {
        val finalSize = scaling.finalDimensions
        val info = SwingPaintInfo(c?.javaClass?.name, x, y, scaling.context.displayDensity, iconScale?.toString(), finalSize.width, finalSize.height)
        IconRenderDebugger.traceSwingPaint(renderer, iconDescriptor, c, info) { renderer.render(swingApi) }
      }
      else {
        renderer.render(swingApi)
      }
    }
  }

  @Suppress("UndesirableClassUsage")
  private fun withLayer(scaling: ResolvedScalingContext, g: Graphics, x: Int, y: Int, painting: (Graphics2D) -> Unit) {
    val w = scaling.finalDimensions.width
    val h = scaling.finalDimensions.height
    // nothing to paint, and BufferedImage rejects an empty size (e.g. an icon whose image is not available)
    if (w <= 0 || h <= 0) return
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sublayer = img.createGraphics()
    try {
      painting(sublayer as Graphics2D)
    } finally {
      sublayer.dispose()
    }
    val g2d = g as Graphics2D
    val baseScale = scaling.context.displayDensity.toDouble()
    val transform = g2d.transform
    try {
      g2d.scale(1.0 / baseScale, 1.0 / baseScale)
      val scaledX = (x * baseScale).roundToInt()
      val scaledY = (y * baseScale).roundToInt()
      g.drawImage(img, scaledX, scaledY, scaledX + w, scaledY + h, 0, 0, img.width, img.height, null)
    } finally {
      g2d.transform = transform
    }
  }

  private fun Graphics?.getDensity(): Float {
    return if (this is Graphics2D) {
      transform.scaleX.toFloat()
    } else 1f
  }

  override fun getIconWidth(): Int {
    return dimensions.width
  }

  override fun getIconHeight(): Int {
    return dimensions.height
  }

  /**
   * Icons of the same descriptor, scale and dark state render the same, so they are equal. For example, `Presentation.getIcon` creates
   * an icon on each call, and its callers compare the icons of two calls. The identity scale `factor(1)` is equal to no scale.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SwingIcon) return false
    return iconDescriptor == other.iconDescriptor && iconScale == other.iconScale && dark == other.dark
  }

  override fun hashCode(): Int = 31 * (31 * iconDescriptor.hashCode() + (iconScale?.hashCode() ?: 0)) + (dark?.hashCode() ?: 0)

  /**
   * Shows the descriptor, with its layers and image paths, for example in the UI Inspector, the debugger and logs.
   * It does not load or measure the icon.
   */
  override fun toString(): String = buildString {
    append("SwingIcon(").append(iconDescriptor)
    if (iconScale != null) append(", scale=").append(iconScale)
    if (dark != null) append(", dark=").append(dark)
    append(")")
  }

  /**
   * Legacy [ScalableIcon] bridge. Platform call sites that resize a Swing [Icon] - most notably the main toolbar's
   * [com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook], which enlarges icons
   * to 20px - speak the [ScalableIcon] contract (`scale(Float)` / `getScale()`), not the new [ScalableSwingIcon] one.
   * Without this, such icons fall through unscaled and render at their native size (e.g. 16px) under the experimental
   * icons pipeline. [getScale] reports the current factor relative to the unscaled origin; [scale] returns a new icon
   * at an absolute factor of that origin (so `scale(1f)` is the native icon), as the [ScalableIcon] contract requires.
   */
  override fun getScale(): Float {
    return when (val current = iconScale) {
      null -> 1f
      is FactorScale -> current.factor.toFloat()
      else -> {
        // Non-factor scale (fit/fill area): derive the linear factor from the rendered size vs. the unscaled origin.
        val originWidth = renderer.resolve(1f, null, ideZoom()).finalDimensions.width
        if (originWidth <= 0) 1f else dimensions.width.toFloat() / originWidth.toFloat()
      }
    }
  }

  /**
   * A factor that is not finite or not positive gives this icon. For example, `loadIconCustomVersionOrScale` divides by the
   * width of the icon, which is 0 for an icon without an image.
   */
  override fun scale(scaleFactor: Float): Icon {
    if (!scaleFactor.isFinite() || scaleFactor <= 0f || scaleFactor == getScale()) return this
    // ScalableIcon.scale is absolute relative to the origin, so build from the unscaled descriptor, not via scaled().
    synchronized(scaledVariants) {
      scaledVariants[scaleFactor]?.get()?.let { return it }
      val icon = SwingIcon(iconDescriptor, factor(scaleFactor), dark)
      scaledVariants[scaleFactor] = SoftReference(icon)
      return icon
    }
  }

  /**
   * Legacy [DarkIconProvider] bridge, so [IconLoader.getDarkIcon] works for this icon. Platform call sites use it for
   * dark or light areas of the UI, for example the main toolbar on a dark header, menus and the editor toolbar.
   * The result renders the same [iconDescriptor] at the same [iconScale] with the dark state [isDark]. It is created once
   * for each dark state and reused.
   *
   * As the legacy icons do, the result replaces the dark state of the icon. If [iconDescriptor] only sets a dark state
   * on another descriptor (see [withDarkState]), the result renders that other descriptor. So a repeated round trip
   * through [com.intellij.platform.icons.IconManager.toIconDescriptor] does not nest the descriptor deeper.
   */
  override fun getDarkIcon(isDark: Boolean): SwingIcon {
    if (dark == isDark) return this
    return (darkOrigin ?: this).darkVariant(isDark)
  }

  /**
   * Legacy [MenuBarIconProvider] bridge for the native menu bar, as `CachedImageIcon.getMenuBarIcon` does it. The icon is
   * rasterized with the dark state [isDark] at the user scale 1: without the IDE zoom and without the [iconScale] of this
   * icon. On macOS, the raster has the system scale of the default screen and comes as a multi-resolution image. Elsewhere,
   * it has the scale 1.
   */
  override fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMultiResolution = SystemInfoRt.isMac
    val sysScale = if (useMultiResolution) JBUIScale.sysScale().toDouble() else 1.0
    val origin = getDarkIcon(isDark).let { if (it.iconScale == null) it else SwingIcon(it.iconDescriptor, null, it.dark) }
    val raster = origin.rasterizeAtUserScaleOne(sysScale.toFloat())
    if (useMultiResolution) {
      return ImageIcon(MultiResolutionImageProvider.convertFromJBImage(JBHiDPIScaledImage(raster, sysScale)))
    }
    return ImageIcon(raster)
  }

  /**
   * The icon as `toStrokeIcon` draws a legacy icon (e.g. the run widget's buttons): the descriptor stroked by
   * [IconModifier.stroke], which takes the hand-drawn `_stroke` variant of an image or reduces it to an outline.
   */
  override fun createStrokeIcon(resultColor: java.awt.Color): Icon {
    val stroke = sRGB(resultColor.red / 255f, resultColor.green / 255f, resultColor.blue / 255f, resultColor.alpha / 255f)
    return SwingIcon(iconDescriptor { icon(this@SwingIcon.iconDescriptor, IconModifier.stroke(stroke)) }, iconScale)
  }

  /**
   * This icon rendered at [density] and the user scale 1, as `CachedImageIcon.getMenuBarIcon` renders it: without the IDE
   * zoom and without the [iconScale]. The renderer paints into the image directly. It measures at the same scale, so the
   * size is the design size times [density], rounded once.
   */
  @Suppress("UndesirableClassUsage")
  private fun rasterizeAtUserScaleOne(density: Float): BufferedImage {
    val scaling = renderer.resolve(density, null, 1f)
    val width = scaling.finalDimensions.width.coerceAtLeast(1)
    val height = scaling.finalDimensions.height.coerceAtLeast(1)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      val context = SwingLayerPaintingContext(null, g, 0, 0, scaling = scaling.context)
      context.ideZoom = 1f
      renderer.render(context)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun darkVariant(isDark: Boolean): SwingIcon {
    if (dark == isDark) return this
    val cached = if (isDark) darkVariant else lightVariant
    if (cached != null) return cached
    val variant = SwingIcon(iconDescriptor.withoutDarkState(), iconScale, isDark)
    variant.darkOrigin = this
    if (isDark) darkVariant = variant else lightVariant = variant
    return variant
  }
}

/**
 * [descriptor] with the dark state [dark]: `true` for the dark artwork, `false` for the light artwork. A dark state that
 * [descriptor] itself sets inside wins over it.
 */
internal fun withDarkState(descriptor: IconDescriptor, dark: Boolean): IconDescriptor =
  DefaultLayeredIconDescriptor(listOf(NestedIconLayer(descriptor, IconManager.modifiers().forceDarkVariant(isDark = dark))))

/**
 * This descriptor without the dark state that it sets on another descriptor as a whole (see [withDarkState]). The dark
 * state can share the modifier with other modifiers, for example with the scale of a scaled dark icon after
 * `IconManager.toIconDescriptor`: then the other modifiers are kept. A dark state inside the other descriptor is kept.
 */
internal fun IconDescriptor.withoutDarkState(): IconDescriptor {
  val layer = (this as? DefaultLayeredIconDescriptor)?.layers?.singleOrNull() as? NestedIconLayer ?: return this
  val modifier = layer.modifier as? ApplyableIconModifier ?: return this
  if (!modifier.hasForcedVariant()) return this
  val rest = modifier.withoutForcedVariant()
  return if (rest == null || rest == RootIconModifier) layer.iconDescriptor else DefaultLayeredIconDescriptor(listOf(NestedIconLayer(layer.iconDescriptor, rest)))
}

private fun ApplyableIconModifier.hasForcedVariant(): Boolean = when (this) {
  is ForceDarkVariantModifier -> true
  is CombinedIconModifier -> root.hasForcedVariant() || other.hasForcedVariant()
  else -> false
}

/** This modifier without its [ForceDarkVariantModifier]s, or `null` if nothing else remains. */
private fun ApplyableIconModifier.withoutForcedVariant(): ApplyableIconModifier? = when (this) {
  is ForceDarkVariantModifier -> null
  is CombinedIconModifier -> {
    val root = root.withoutForcedVariant()
    val other = other.withoutForcedVariant()
    when {
      root == null -> other
      other == null -> root
      else -> CombinedIconModifier(root, other)
    }
  }
  else -> this
}

/**
 * The IDE zoom, the user scale of Swing. Images are measured at their design size, so the renderer applies the zoom to
 * every unit of an icon, as Swing applies it to logical pixels.
 */
private fun ideZoom(): Float = JBUIScale.scale(1f)

/** The number of scaled icons that [SwingIcon.scale] keeps, as `ScaledIconCache` of `CachedImageIcon` does. */
private const val SCALED_VARIANT_LIMIT = 8
