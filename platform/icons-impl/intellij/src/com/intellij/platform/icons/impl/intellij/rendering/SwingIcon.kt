// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.modifiers.ApplyableIconModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.DarkStateModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.modifiers.then
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.ui.icons.StrokeIconProvider
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
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
 * The icon keeps its state in separate parts, and [iconDescriptor] is derived from them in one order: [rawDescriptor],
 * then each limited fit of [scaled] that a factor cannot absorb, then the stroke of [createStrokeIcon]. [iconScale] scales
 * the whole icon. [dark] is the explicit dark state of the icon: `true` for the dark artwork, `false` for the light
 * artwork. `null` follows the theme. It is an explicit dark state of the rendering context, so a dark state set inside
 * [iconDescriptor] wins over it. The [IconLoader.getDarkIcon] protocol ([DarkIconProvider]) changes only [dark], and a
 * scale or a stroke keeps it. So no change removes a state that another change added.
 *
 * As a descriptor (see [toDescriptor]), the scale and the dark state are one nested layer with a scale and a
 * [DarkStateModifier]. The constructor reads such a layer back (see [parse]), so a round trip gives an equal icon.
 */
internal class SwingIcon private constructor(
  private val state: State,
  /** The dark variants of the icons that [getDarkIcon] made from one icon, shared by all of them. */
  private val darkVariants: DarkVariants,
): Icon, ScalableSwingIcon, ScalableIcon, StrokeIconProvider, DarkIconProvider, MenuBarIconProvider {
  /** An icon of [iconDescriptor] with the scale [scale] and the dark state [dark], see [parse]. */
  constructor(iconDescriptor: IconDescriptor, scale: IconScale? = null, dark: Boolean? = null) :
    this(parse(iconDescriptor, scale, dark), DarkVariants())

  /**
   * The state of a [SwingIcon]. [boundedFits] are the limited fits that a factor cannot absorb, from the inside out (see
   * [scaled]). [stroke] is the color of [createStrokeIcon], or `null`.
   */
  private data class State(
    val rawDescriptor: IconDescriptor,
    val boundedFits: List<FitAreaScale> = emptyList(),
    val stroke: Color? = null,
    val scale: IconScale? = null,
    val dark: Boolean? = null,
  )

  /** The dark and the light variant that [getDarkIcon] made. */
  private class DarkVariants {
    @Volatile
    var dark: SwingIcon? = null

    @Volatile
    var light: SwingIcon? = null
  }

  /** The descriptor that the icon was created from, without the scale and the dark state that the icon adds. */
  val rawDescriptor: IconDescriptor get() = state.rawDescriptor

  /** The dark state of the icon, see [SwingIcon]. */
  val dark: Boolean? get() = state.dark

  /**
   * The identity factor scale ([IconScale.Default] == `factor(1)`) carries no size change, so normalize it to `null`.
   * This lets [com.intellij.platform.icons.impl.intellij.IntelliJIconManager.toIconDescriptor] unwrap back to the native
   * [iconDescriptor] instead of wrapping it in a scale layer. Without it, the Presentation write-bridge fails to
   * recognize its own icon and re-wraps the descriptor on every update cycle - each pass nesting another IconIconLayer +
   * identity ScaleModifier - which grows the descriptor without bound and buries the native image so the toolbar look's
   * enlargement never reaches a clean descriptor.
   */
  val iconScale: IconScale? = state.scale.normalized()

  /** The descriptor that the icon renders, without [iconScale] and [dark]: see [SwingIcon]. */
  val iconDescriptor: IconDescriptor = state.boundedFits
    .fold(state.rawDescriptor) { descriptor, fit -> nested(descriptor, IconModifier.scale(fit)) }
    .let { descriptor -> state.stroke?.let { nested(descriptor, IconModifier.stroke(it)) } ?: descriptor }

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

  /** The icons that [getMenuBarIcon] rasterizes, for the light and the dark state. They do not render in the UI. */
  @Volatile
  private var menuBarOrigins: Pair<SwingIcon?, SwingIcon?> = null to null

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
    IconRendererManager.getInstance().createRenderer(iconDescriptor, renderingContext)
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

  /** A new icon with [state]. It has its own dark variants. */
  private fun with(state: State): SwingIcon = SwingIcon(state, DarkVariants())

  override fun scaled(scale: IconScale): ScalableSwingIcon {
    val current = iconScale
    return when {
      current is FactorScale && scale is FactorScale -> with(state.copy(scale = factor(scale.factor * current.factor)))
      // a limited fit cannot absorb the factor (a 16px icon in a 2x larger "at most" area stays 16px): fit, then scale
      current is FitAreaScale && scale is FactorScale && (!current.upscale || !current.downscale) ->
        with(state.copy(boundedFits = state.boundedFits + current, scale = scale))
      current is FitAreaScale && scale is FactorScale ->
        with(state.copy(scale = fitArea(current.width * scale.factor, current.height * scale.factor, current.relative)))
      else -> with(state.copy(scale = scale))
    }
  }

  /**
   * This icon as a descriptor: [iconDescriptor], in one nested layer with [iconScale] and the [DarkStateModifier] of
   * [dark] if the icon has them. The constructor reads that layer back, see [parse].
   */
  fun toDescriptor(): IconDescriptor = stateModifier()?.let { nested(iconDescriptor, it) } ?: iconDescriptor

  /** The modifier of [iconScale] and [dark], or `null` if the icon has neither. */
  fun stateModifier(): IconModifier? {
    if (iconScale == null && dark == null) return null
    var modifier: IconModifier = IconModifier
    iconScale?.let { modifier = modifier.scale(it) }
    dark?.let { modifier = modifier then DarkStateModifier(it) }
    return modifier
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
      renderer.render(swingApi)
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
   * Icons of the same state render the same, so they are equal. For example, `Presentation.getIcon` creates an icon on
   * each call, and its callers compare the icons of two calls. The identity scale `factor(1)` is equal to no scale.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SwingIcon) return false
    return state.copy(scale = iconScale) == other.state.copy(scale = other.iconScale)
  }

  override fun hashCode(): Int = state.copy(scale = iconScale).hashCode()

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
    // ScalableIcon.scale is absolute relative to the origin, so replace the scale, not via scaled().
    synchronized(scaledVariants) {
      scaledVariants[scaleFactor]?.get()?.let { return it }
      val icon = with(state.copy(scale = factor(scaleFactor)))
      scaledVariants[scaleFactor] = SoftReference(icon)
      return icon
    }
  }

  /**
   * Legacy [DarkIconProvider] bridge, so [IconLoader.getDarkIcon] works for this icon. Platform call sites use it for
   * dark or light areas of the UI, for example the main toolbar on a dark header, menus and the editor toolbar.
   * The result has the state of this icon with the dark state [isDark]. As the legacy icons do, the result replaces the
   * dark state of the icon. A dark state that [iconDescriptor] sets inside stays. The variants of one icon are created
   * once and shared, so a toggle back gives the same icon.
   */
  override fun getDarkIcon(isDark: Boolean): SwingIcon {
    if (dark == isDark) return this
    val cached = if (isDark) darkVariants.dark else darkVariants.light
    if (cached != null) return cached
    val variant = SwingIcon(state.copy(dark = isDark), darkVariants)
    if (isDark) darkVariants.dark = variant else darkVariants.light = variant
    return variant
  }

  /**
   * Legacy [MenuBarIconProvider] bridge for the native menu bar, as `CachedImageIcon.getMenuBarIcon` does it. The icon is
   * rasterized with the dark state [isDark] at the user scale 1: without the IDE zoom and without the [iconScale] of this
   * icon. On macOS, the raster has the system scale of the default screen and comes as a multi-resolution image. Elsewhere,
   * it has the scale 1. It rasterizes its own icon, so it never changes the renderer of an icon that the UI paints.
   */
  override fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMultiResolution = SystemInfoRt.isMac
    val sysScale = if (useMultiResolution) JBUIScale.sysScale().toDouble() else 1.0
    val origin = menuBarOrigin(isDark)
    val raster = origin.rasterizeAtUserScaleOne(sysScale.toFloat())
    if (useMultiResolution) {
      return ImageIcon(MultiResolutionImageProvider.convertFromJBImage(JBHiDPIScaledImage(raster, sysScale)))
    }
    return ImageIcon(raster)
  }

  /**
   * The icon as `toStrokeIcon` draws a legacy icon (e.g. the run widget's buttons): the descriptor stroked by
   * [IconModifier.stroke], which takes the hand-drawn `_stroke` variant of an image or reduces it to an outline. The
   * scale and the dark state stay. A stroke replaces the stroke of a stroke icon, as the legacy `toStrokeIcon` does.
   */
  override fun createStrokeIcon(resultColor: java.awt.Color): Icon {
    val stroke = sRGB(resultColor.red / 255f, resultColor.green / 255f, resultColor.blue / 255f, resultColor.alpha / 255f)
    return with(state.copy(stroke = stroke))
  }

  /**
   * This icon rendered at [density] and the user scale 1, as `CachedImageIcon.getMenuBarIcon` renders it: without the IDE
   * zoom and without the [iconScale]. The renderer paints into the image directly. It measures at the same scale, so the
   * size is the design size times [density], rounded once. A bridged legacy icon of a fixed size keeps its own size:
   * the renderer takes the zoom of the context out of its logical size, see `legacySize`.
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

  private companion object {
    /**
     * The state of an icon of [descriptor] with [scale] and [dark]. A descriptor of a Swing icon (see [toDescriptor]) is
     * one nested layer with a [DarkStateModifier] and an optional scale. That layer gives the dark state and the scale,
     * unless [dark] or [scale] is set. The layer is not read in other cases, and the descriptor renders it as it is.
     */
    fun parse(descriptor: IconDescriptor, scale: IconScale?, dark: Boolean?): State {
      val explicitScale = scale.normalized()
      val plain = State(descriptor, scale = explicitScale, dark = dark)
      if (dark != null) return plain
      val layer = (descriptor as? DefaultLayeredIconDescriptor)?.layers?.singleOrNull() as? NestedIconLayer ?: return plain
      val parts = (layer.modifier as? ApplyableIconModifier)?.leaves()?.filter { it != RootIconModifier } ?: return plain
      val darkState = parts.filterIsInstance<DarkStateModifier>().singleOrNull() ?: return plain
      val scales = parts.filterIsInstance<ScaleModifier>()
      if (parts.size != 1 + scales.size || scales.size > 1) return plain
      val layerScale = scales.singleOrNull()?.scale.normalized()
      if (layerScale != null && explicitScale != null) return plain
      return State(layer.iconDescriptor, scale = explicitScale ?: layerScale, dark = darkState.isDark)
    }
  }

  /** The icon that [getMenuBarIcon] rasterizes: this icon without [iconScale], with the dark state [isDark]. */
  private fun menuBarOrigin(isDark: Boolean): SwingIcon {
    val (forLight, forDark) = menuBarOrigins
    (if (isDark) forDark else forLight)?.let { return it }
    val origin = with(state.copy(scale = null, dark = isDark))
    menuBarOrigins = if (isDark) forLight to origin else origin to forDark
    return origin
  }
}

/** [descriptor] in one nested layer with [modifier]. */
private fun nested(descriptor: IconDescriptor, modifier: IconModifier): IconDescriptor =
  DefaultLayeredIconDescriptor(listOf(NestedIconLayer(descriptor, modifier)))

private fun IconScale?.normalized(): IconScale? = this?.takeUnless { it is FactorScale && it.factor == 1.0 }

/** The modifiers that this modifier combines, in order. */
private fun ApplyableIconModifier.leaves(): List<ApplyableIconModifier> = when (this) {
  is CombinedIconModifier -> root.leaves() + other.leaves()
  else -> listOf(this)
}

/**
 * The IDE zoom, the user scale of Swing. Images are measured at their design size, so the renderer applies the zoom to
 * every unit of an icon, as Swing applies it to logical pixels.
 */
private fun ideZoom(): Float = JBUIScale.scale(1f)

/** The number of scaled icons that [SwingIcon.scale] keeps, as `ScaledIconCache` of `CachedImageIcon` does. */
private const val SCALED_VARIANT_LIMIT = 8
