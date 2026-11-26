// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.util.SVGLoader
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.swing.toAwtColor
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.colorPatchedIcon
import java.awt.Component
import java.awt.Image
import java.awt.image.RGBImageFilter
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.math.roundToInt

internal class LegacyIconImageResourceHolder(
  private val backingLegacyIcon: Icon
): SwingImageResourceHolder {
  /**
   * The `@WxH` variants, by the path of the backing icon and W×H: [NO_VARIANT] for a variant that does not exist or is not
   * drawn for W×H. A lookup runs on each paint, so a hit and a miss are both kept.
   */
  private val variants = ConcurrentHashMap<Triple<String, Int, Int>, Any>()

  /**
   * The size of the legacy icon without the IDE zoom: the renderer applies the zoom, see `SwingIcon`. For a
   * `CachedImageIcon`, it is the design size, read at the unit scale. Another icon can have a fixed size, so its size is
   * divided by the current IDE zoom; `SwingIconLayerRenderer` reads it again when the zoom changes.
   */
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    (backingLegacyIcon as? CachedImageIcon)?.let { cached ->
      val design = cached.unitScaled(cached.getObjScale() ?: 1.0)
      if (design.iconWidth > 0 && design.iconHeight > 0) return Dimensions(design.iconWidth, design.iconHeight)
    }
    val zoom = JBUIScale.scale(1f)
    return Dimensions((backingLegacyIcon.iconWidth / zoom).roundToInt(), (backingLegacyIcon.iconHeight / zoom).roundToInt())
  }

  /**
   * The legacy icon applies its own color patcher (the theme palette, as a rule) and the global filter itself. They come
   * from the global theme state of the legacy icons, not from [theme]. [theme] does not apply to a bridged legacy icon:
   * the icon follows the global legacy state, also for its dark state. Only an explicit dark state in [imageModifiers],
   * `IconModifier.forceDarkVariant` or `IconModifier.forceLightVariant`, replaces it.
   */
  override fun getImage(
    scale: ScaleContext,
    painted: PaintedSize?,
    imageModifiers: ImageModifiers?,
    theme: ThemeContext,
    component: Component?,
  ): Image? {
    val (source, sourceScale) = painted?.let { sizeVariant(scale, it, imageModifiers.sizeVariantPolicy()) } ?: (backingLegacyIcon to scale)
    // `createWithPatcher` replaces the own patcher of the legacy icon, so our patches run on top of it.
    val rootPatcher = (patchTarget(source) as? CachedImageIcon)?.svgColorPatcher
    val svgPatcher = imageModifiers.ownColorPatcher(rootPatcher = rootPatcher)
    val params = imageModifiers.toLoadParameters(rootPatcher = rootPatcher, globalFilter = null, ownPatcher = svgPatcher)
    // Without an explicit dark state, the legacy icon keeps its own one. It follows the theme unless it is set.
    // A stroked icon loads the light artwork, see `toLoadParameters`.
    val forcedDark = (imageModifiers as? DefaultImageModifiers)?.forcedDark?.takeUnless { params.isStroke }
    val icon = when {
      svgPatcher != null -> patched(source, svgPatcher, params, imageModifiers, forcedDark)
      forcedDark != null -> IconLoader.getDarkIcon(source, forcedDark)
      else -> source
    }
    val filtered = if (params.filters.isNotEmpty()) {
        IconLoader.filterIcon(icon = icon, filterSupplier = object : RgbImageFilterSupplier {
            override fun getFilter() = params.filters.first() as RGBImageFilter
        })
    } else icon
    // pass the component on, as Swing would when painting the icon directly: some icons fail without one
    return IconLoader.toImage(filtered, sourceScale, component)
  }

  /**
   * The hand-drawn `@WxH` variant [policy] selects for a backing `CachedImageIcon` at the logical size [painted] (see
   * [SizeVariants]), carrying the original icon's modifications (dark state, filters, patcher) as legacy
   * `loadIconCustomVersion` does via `withAnotherIconModifications`; `null` if none applies.
   */
  private fun sizeVariant(scale: ScaleContext, painted: PaintedSize, policy: SizeVariantPolicy): Pair<Icon, ScaleContext>? {
    if (policy == SizeVariantPolicy.None || scale.getScale(ScaleType.OBJ_SCALE) == 1.0) return null
    val cached = backingLegacyIcon as? CachedImageIcon ?: (backingLegacyIcon as? RetrievableIcon)?.retrieveIcon() as? CachedImageIcon
                 ?: return null
    val design = getExpectedDimensions(imageModifiers = null)
    // the effective path: the theme path patch is applied lazily, on the first size request
    val (path, classLoader) = cached.getCoords() ?: return null
    val zoom = scale.getScale(ScaleType.USR_SCALE)
    val (width, height) = SizeVariants.chooseVariant(policy, painted, zoom, design.width, design.height) { w, h ->
      variant(path, classLoader, w, h) != null
    } ?: return null
    val found = variant(path, classLoader, width, height) ?: return null
    return found.withAnotherIconModifications(cached) to SizeVariantSupport.variantScale(scale, design.width, width)
  }

  /** The `@WxH` variant of [path], if it exists and, like the legacy check, is drawn for W×H. */
  private fun variant(path: String, classLoader: ClassLoader, width: Int, height: Int): CachedImageIcon? {
    val result = variants.computeIfAbsent(Triple(path, width, height)) {
      val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height)
      val found = variantPath?.let { findIconUsingNewImplementation(it, classLoader) as? CachedImageIcon }
      val design = found?.unitScaled(1.0)
      if (found != null && design != null && design.iconWidth == width && design.iconHeight == height) found else NO_VARIANT
    }
    return result as? CachedImageIcon
  }
}

/** The icon that the patches apply to: for a `RetrievableIcon` that is no `CachedImageIcon`, the icon it retrieves. */
private fun patchTarget(source: Icon): Icon = if (source !is CachedImageIcon && source is RetrievableIcon) source.retrieveIcon() else source

/**
 * [source] with [svgPatcher] and the dark state [forcedDark]. A `CachedImageIcon` takes the patcher itself. A legacy
 * composite, such as a `RowIcon`, a `LayeredIcon` or a `LazyIcon`, is patched in each `CachedImageIcon` in it, as the
 * legacy `toStrokeIcon` and `colorPatchedIcon` do. A `RetrievableIcon` is patched in the icon it retrieves, see
 * [patchTarget].
 */
private fun patched(
  source: Icon,
  svgPatcher: SVGLoader.SvgElementColorPatcherProvider,
  params: LoadIconParameters,
  imageModifiers: ImageModifiers?,
  forcedDark: Boolean?,
): Icon {
  val target = patchTarget(source)
  if (target is CachedImageIcon) {
    return target.createWithPatcher(svgPatcher, isDark = if (params.isStroke) false else forcedDark, useStroke = params.isStroke)
  }
  val stroke = (imageModifiers as? DefaultImageModifiers)?.stroke
  if (stroke != null) return toStrokeIcon(target, stroke.toAwtColor())
  return colorPatchedIcon(forcedDark?.let { IconLoader.getDarkIcon(target, it) } ?: target, svgPatcher)
}

/** This icon at the unit scale with the object scale [objScale]: its design size times [objScale]. */
private fun CachedImageIcon.unitScaled(objScale: Double): Icon =
  scale(ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(objScale), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0))))

private val NO_VARIANT = Any()
