// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.DarkModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.dark
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.SwingPaintInfo
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.impl.rendering.resolve
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
import java.util.concurrent.ConcurrentHashMap
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
class SwingIcon(
  val iconDescriptor: IconDescriptor,
  scale: IconScale? = null,
  val dark: Boolean? = null,
): Icon, ScalableSwingIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider {
  /** Keeps the constructor signature from before [dark] existed (binary compatibility). */
  constructor(iconDescriptor: IconDescriptor, scale: IconScale?) : this(iconDescriptor, scale, null)

  /**
   * The identity factor scale ([IconScale.Default] == `factor(1)`) carries no size change, so normalize it to `null`.
   * This lets [com.intellij.platform.icons.impl.intellij.IntelliJIconManager.toIconDescriptor] unwrap back to the native
   * [iconDescriptor] instead of wrapping it in a scale layer. Without it, the Presentation write-bridge fails to
   * recognize its own icon and re-wraps the descriptor on every update cycle - each pass nesting another IconIconLayer +
   * identity ScaleModifier - which grows the descriptor without bound and buries the native image so the toolbar look's
   * enlargement never reaches a clean descriptor.
   */
  val scale: IconScale? = scale?.takeUnless { it is FactorScale && it.factor == 1.0 }

  internal val users = WeakHashMap<Component, Boolean>()

  // [scale] runs on the paint path (e.g. the main toolbar enlarging icons to 20px on every repaint); cache the scaled
  // variants so repaints reuse the renderer instead of rebuilding it, mirroring CachedImageIcon's own scale cache.
  private val scaledVariants = ConcurrentHashMap<Float, SwingIcon>()

  // [getDarkIcon] runs on the paint path too (e.g. the main toolbar on a dark header on every repaint).
  @Volatile
  private var darkVariant: SwingIcon? = null

  @Volatile
  private var lightVariant: SwingIcon? = null

  /** The icon that [getDarkIcon] made this icon from, so that the variants of one icon are not nested. */
  private var darkOrigin: SwingIcon? = null

  private val renderer by lazy {
    val flow = IconRendererManager.createUpdateFlow(null) {
      withContext(Dispatchers.EDT) {
        for (user in users.keys) {
          user.repaint()
        }
      }
    }
    val context = IconRendererManager.createRenderingContext(flow, dark?.let { DefaultImageModifiers(darkOverride = it) })
    // [dark] replaces a dark state that the descriptor only sets on another descriptor, as in [getDarkIcon]
    val descriptor = if (dark != null) iconDescriptor.withoutDarkState() else iconDescriptor
    IconRendererManager.getInstance().createRenderer(descriptor, context)
  }

  private val dimensions by lazy {
    renderer.resolve(1f, scale).finalDimensions
  }

  override fun scaled(scale: IconScale): ScalableSwingIcon {
    val mergedScale = if (this.scale != null) {
      when (scale) {
        is FactorScale if this.scale is FactorScale -> {
          factor(scale.factor * this.scale.factor)
        }
        is FactorScale if this.scale is FitAreaScale -> {
          fitArea(this.scale.width * scale.factor, this.scale.height * scale.factor, this.scale.relative)
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
    val scaling = renderer.resolve(g.getDensity(), scale)
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
        val info = SwingPaintInfo(c?.javaClass?.name, x, y, scaling.context.displayDensity, scale?.toString(), finalSize.width, finalSize.height)
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
    if (w < 0 || h < 0) return
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sublayer = img.createGraphics()
    try {
      painting(sublayer as Graphics2D)
      val g2d = g as Graphics2D
      val baseScale = scaling.context.displayDensity.toDouble()
      g2d.scale(1.0 / baseScale, 1.0 / baseScale)
      val scaledX = (x * baseScale).roundToInt()
      val scaledY = (y * baseScale).roundToInt()
      g.drawImage(img, scaledX, scaledY, scaledX + w, scaledY + h, 0, 0, img.width, img.height, null)
      g2d.scale(baseScale, baseScale)
    } finally {
      sublayer.dispose()
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
   * Exposes the wrapped [iconDescriptor] so tools that print a Swing [Icon] - the UI Inspector's icon cell
   * ([com.intellij.internal.inspector.components.ValueCellRenderer], the debugger, logs - reveal the descriptor's
   * insides: its layers and, for image layers, the resource paths they load. Without this the inspector would only
   * show the default `SwingIcon@hash`.
   */
  override fun toString(): String {
    // Keep this cheap and total: the UI Inspector / debugger call toString on arbitrary icons and must not throw.
    val sizeInfo = try { "${iconWidth}x${iconHeight}" } catch (e: Throwable) { "unresolved" }
    val factorInfo = try { getScale().toString() } catch (e: Throwable) { "?" }
    return buildString {
      append("SwingIcon(size=").append(sizeInfo)
      append(", factor=").append(factorInfo)
      if (scale != null) append(", iconScale=").append(scale)
      if (dark != null) append(", dark=").append(dark)
      append(", descriptor=").append(iconDescriptor)
      append(")")
    }
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
    return when (val current = scale) {
      null -> 1f
      is FactorScale -> current.factor.toFloat()
      else -> {
        // Non-factor scale (fit/fill area): derive the linear factor from the rendered size vs. the unscaled origin.
        val originWidth = renderer.resolve(1f, null).finalDimensions.width
        if (originWidth <= 0) 1f else dimensions.width.toFloat() / originWidth.toFloat()
      }
    }
  }

  override fun scale(scaleFactor: Float): Icon {
    if (scaleFactor == getScale()) return this
    // ScalableIcon.scale is absolute relative to the origin, so build from the unscaled descriptor, not via scaled().
    return scaledVariants.getOrPut(scaleFactor) { SwingIcon(iconDescriptor, factor(scaleFactor), dark) }
  }

  /**
   * Legacy [DarkIconProvider] bridge, so [IconLoader.getDarkIcon] works for this icon. Platform call sites use it for
   * dark or light areas of the UI, for example the main toolbar on a dark header, menus and the editor toolbar.
   * The result renders the same [iconDescriptor] at the same [scale] with the dark state [isDark]. It is created once
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
   * rasterized with the dark state [isDark] at the user scale 1: without the IDE zoom and without the [scale] of this
   * icon. On macOS, the raster has the system scale of the default screen and comes as a multi-resolution image. Elsewhere,
   * it has the scale 1.
   */
  override fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMultiResolution = SystemInfoRt.isMac
    val sysScale = if (useMultiResolution) JBUIScale.sysScale().toDouble() else 1.0
    val zoom = JBUIScale.scale(1f)
    val origin = getDarkIcon(isDark).let { if (it.scale == null) it else SwingIcon(it.iconDescriptor, null, it.dark) }
    // The device density without the IDE zoom paints the icon in the device pixels of the user scale 1.
    val raster = origin.rasterize(sysScale / zoom, sysScale)
    if (useMultiResolution) {
      return ImageIcon(MultiResolutionImageProvider.convertFromJBImage(JBHiDPIScaledImage(raster, sysScale)))
    }
    return ImageIcon(raster)
  }

  /** This icon painted at [density], in an image of the size of this icon at [density], scaled by [sizeScale]. */
  @Suppress("UndesirableClassUsage")
  private fun rasterize(density: Double, sizeScale: Double): BufferedImage {
    val width = (iconWidth * sizeScale / JBUIScale.scale(1f)).roundToInt().coerceAtLeast(1)
    val height = (iconHeight * sizeScale / JBUIScale.scale(1f)).roundToInt().coerceAtLeast(1)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(density, density)
      paintIcon(null, g, 0, 0)
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
    val variant = SwingIcon(iconDescriptor.withoutDarkState(), scale, isDark)
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
  DefaultLayeredIconDescriptor(listOf(NestedIconLayer(descriptor, IconModifier.dark(dark))))

/** The descriptor that this descriptor sets a dark state on, if it only does that (see [withDarkState]); else this descriptor. */
internal fun IconDescriptor.withoutDarkState(): IconDescriptor {
  val layer = (this as? DefaultLayeredIconDescriptor)?.layers?.singleOrNull() as? NestedIconLayer ?: return this
  val modifier = layer.modifier
  val onlyDark = modifier is DarkModifier ||
                 (modifier is CombinedIconModifier && modifier.root == RootIconModifier && modifier.other is DarkModifier)
  return if (onlyDark) layer.iconDescriptor else this
}

