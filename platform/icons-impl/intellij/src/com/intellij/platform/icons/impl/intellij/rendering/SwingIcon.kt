// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.ui.icons.StrokeIconProvider
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.ui.scale.JBUIScale
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

internal class SwingIcon(
  val iconDescriptor: IconDescriptor,
  scale: IconScale? = null
): Icon, ScalableSwingIcon, ScalableIcon, StrokeIconProvider {
  /**
   * The identity factor scale ([IconScale.Default] == `factor(1)`) carries no size change, so normalize it to `null`.
   * This lets [com.intellij.platform.icons.impl.intellij.IntelliJIconManager.toIconDescriptor] unwrap back to the native
   * [iconDescriptor] instead of wrapping it in a scale layer. Without it, the Presentation write-bridge fails to
   * recognize its own icon and re-wraps the descriptor on every update cycle - each pass nesting another IconIconLayer +
   * identity ScaleModifier - which grows the descriptor without bound and buries the native image so the toolbar look's
   * enlargement never reaches a clean descriptor.
   */
  val iconScale: IconScale? = scale?.takeUnless { it is FactorScale && it.factor == 1.0 }

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

  private val renderer by lazy {
    val flow = IconRendererManager.createUpdateFlow(null) {
      withContext(Dispatchers.EDT) {
        for (user in users.keys) {
          user.repaint()
        }
      }
    }
    val context = IconRendererManager.createRenderingContext(flow)
    IconRendererManager.getInstance().createRenderer(iconDescriptor, context)
  }

  /** The size at the current IDE zoom and theme. It is measured on each call, so a zoom or a theme change applies. */
  private val dimensions: Dimensions
    get() = renderer.resolve(1f, iconScale, ideZoom()).finalDimensions

  override fun scaled(scale: IconScale): ScalableSwingIcon {
    val mergedScale = if (this.iconScale != null) {
      when (scale) {
        is FactorScale if this.iconScale is FactorScale -> {
          factor(scale.factor * this.iconScale.factor)
        }
        // a limited fit cannot absorb the factor (a 16px icon in a 2x larger "at most" area stays 16px): fit, then scale
        is FactorScale if this.iconScale is FitAreaScale && (!this.iconScale.upscale || !this.iconScale.downscale) -> {
          val fit = this.iconScale
          return SwingIcon(iconDescriptor { icon(this@SwingIcon.iconDescriptor, IconModifier.scale(fit)) }, scale)
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
    return SwingIcon(iconDescriptor, mergedScale)
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
   * Icons of the same descriptor and scale render the same, so they are equal. For example, `Presentation.getIcon` creates
   * an icon on each call, and its callers compare the icons of two calls. The identity scale `factor(1)` is equal to no scale.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SwingIcon) return false
    return iconDescriptor == other.iconDescriptor && iconScale == other.iconScale
  }

  override fun hashCode(): Int = 31 * iconDescriptor.hashCode() + (iconScale?.hashCode() ?: 0)

  /**
   * Shows the descriptor, with its layers and image paths, for example in the UI Inspector, the debugger and logs.
   * It does not load or measure the icon.
   */
  override fun toString(): String =
    if (iconScale == null) "SwingIcon($iconDescriptor)" else "SwingIcon($iconDescriptor, scale=$iconScale)"

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
      val icon = SwingIcon(iconDescriptor, factor(scaleFactor))
      scaledVariants[scaleFactor] = SoftReference(icon)
      return icon
    }
  }

  /**
   * The icon as `toStrokeIcon` draws a legacy icon (e.g. the run widget's buttons): the descriptor stroked by
   * [IconModifier.stroke], which takes the hand-drawn `_stroke` variant of an image or reduces it to an outline.
   */
  override fun createStrokeIcon(resultColor: java.awt.Color): Icon {
    val stroke = sRGB(resultColor.red / 255f, resultColor.green / 255f, resultColor.blue / 255f, resultColor.alpha / 255f)
    return SwingIcon(iconDescriptor { icon(this@SwingIcon.iconDescriptor, IconModifier.stroke(stroke)) }, iconScale)
  }
}

/**
 * The IDE zoom, the user scale of Swing. Images are measured at their design size, so the renderer applies the zoom to
 * every unit of an icon, as Swing applies it to logical pixels.
 */
private fun ideZoom(): Float = JBUIScale.scale(1f)

/** The number of scaled icons that [SwingIcon.scale] keeps, as `ScaledIconCache` of `CachedImageIcon` does. */
private const val SCALED_VARIANT_LIMIT = 8
