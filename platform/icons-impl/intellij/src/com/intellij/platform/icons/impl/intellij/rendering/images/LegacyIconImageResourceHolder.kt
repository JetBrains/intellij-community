// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.impl.rendering.SizeVariantCache
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.util.SVGLoader
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.swing.toAwtColor
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.colorPatchedIcon
import java.awt.Component
import java.awt.Image
import java.awt.image.RGBImageFilter
import javax.swing.Icon

internal class LegacyIconImageResourceHolder(
  private val backingLegacyIcon: Icon
): SwingImageResourceHolder {
  /**
   * The `@WxH` variants of the effective path of the backing icon. The theme can patch that path, so the variants are
   * looked up again when it changes.
   */
  @Volatile
  private var variants: Pair<String, SizeVariantCache<CachedImageIcon>>? = null

  /**
   * The size of the legacy icon, see [legacySize]. For a `CachedImageIcon`, also one that a `RetrievableIcon` gives, it
   * is the design size, without the IDE zoom: the renderer applies the zoom, see `SwingIcon`. Another icon gives its
   * logical size.
   */
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    val size = legacySize(backingLegacyIcon)
    return Dimensions(size.width, size.height)
  }

  override fun getImage(scale: ScaleContext, painted: PaintedSize?, imageModifiers: ImageModifiers?, component: Component?): Image? {
    val params = imageModifiers.toLoadParameters()
    val (source, sourceScale) = painted?.let { sizeVariant(scale, it, imageModifiers.sizeVariantPolicy()) } ?: (backingLegacyIcon to withOwnScale(scale))
    val icon = patched(source, params, imageModifiers)
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
    if (policy == SizeVariantPolicy.None) return null
    val cached = backingLegacyIcon.cachedImageIcon() ?: return null
    val design = getExpectedDimensions(imageModifiers = null)
    // the effective path: the theme path patch is applied lazily, on the first size request
    val (path, classLoader) = cached.getCoords() ?: return null
    val cache = variantsOf(path)
    val variant: (Int, Int) -> CachedImageIcon? = { w, h -> cache.get(w, h) { findVariant(path, classLoader, w, h) } }
    val zoom = scale.getScale(ScaleType.USR_SCALE)
    val (width, height) = SizeVariants.chooseVariant(policy, painted, zoom, design.width, design.height) { w, h ->
      variant(w, h) != null
    } ?: return null
    val found = variant(width, height) ?: return null
    return found.withAnotherIconModifications(cached) to SizeVariantSupport.variantScale(scale, design.width, width)
  }

  /**
   * [scale] with the object scale of a backing `CachedImageIcon`, for example from `IconUtil.scale`. `IconLoader.toImage`
   * uses [scale] in place of the scale of the icon. The design size contains the object scale, see [legacySize]. So
   * without it, the image has the size of the icon without its object scale, and the renderer stretches it.
   */
  private fun withOwnScale(scale: ScaleContext): ScaleContext {
    val objScale = backingLegacyIcon.cachedImageIcon()?.getObjScale()?.takeIf { it != 1.0 } ?: return scale
    return scale.copyWithScale(ScaleType.OBJ_SCALE.of(scale.getScale(ScaleType.OBJ_SCALE) * objScale))
  }

  private fun variantsOf(path: String): SizeVariantCache<CachedImageIcon> {
    variants?.let { (variantsPath, cache) -> if (variantsPath == path) return cache }
    return SizeVariantCache<CachedImageIcon>().also { variants = path to it }
  }
}

/** The `@WxH` variant of [path], if it exists and, like the legacy check, is drawn for W×H. */
private fun findVariant(path: String, classLoader: ClassLoader, width: Int, height: Int): CachedImageIcon? {
  val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height) ?: return null
  val found = findIconUsingNewImplementation(variantPath, classLoader) as? CachedImageIcon ?: return null
  val design = found.unitScaled(1.0)
  return found.takeIf { design.iconWidth == width && design.iconHeight == height }
}

/** The `CachedImageIcon` that this icon is, or that it gives as a `RetrievableIcon`; `null` for another icon. */
internal fun Icon.cachedImageIcon(): CachedImageIcon? =
  this as? CachedImageIcon ?: (this as? RetrievableIcon)?.retrieveIcon() as? CachedImageIcon

/**
 * The size of a legacy icon. For a `CachedImageIcon` (see [cachedImageIcon]), it is the design size, read at the unit
 * scale: the renderer applies the IDE zoom to it. Another icon gives its size in logical pixels at the current IDE zoom,
 * as Swing paints it: [isLogical] is `true`. Such a size can be fixed or follow the zoom, and the renderer cannot tell
 * which. So the renderer takes the zoom of its context out of it, see `CompoundSize.withoutZoom`. A 17×17 icon then
 * stays 17×17 at each zoom, also in a render without the zoom, such as the menu bar icon of `SwingIcon`.
 */
internal class LegacySize(val width: Int, val height: Int, val isLogical: Boolean)

/** The size of [icon], see [LegacySize]. */
internal fun legacySize(icon: Icon): LegacySize {
  icon.cachedImageIcon()?.let { cached ->
    val design = cached.unitScaled(cached.getObjScale() ?: 1.0)
    if (design.iconWidth > 0 && design.iconHeight > 0) return LegacySize(design.iconWidth, design.iconHeight, isLogical = false)
  }
  return LegacySize(icon.iconWidth, icon.iconHeight, isLogical = true)
}

/**
 * [source] with the SVG patcher of [params]. A `CachedImageIcon` takes the patcher itself. A legacy composite, such as a
 * `RowIcon`, a `LayeredIcon` or a `LazyIcon`, is patched in each `CachedImageIcon` in it, as the legacy `toStrokeIcon` and
 * `colorPatchedIcon` do. A `RetrievableIcon` is patched in the icon it retrieves.
 */
private fun patched(source: Icon, params: LoadIconParameters, imageModifiers: ImageModifiers?): Icon {
  val svgPatcher = params.colorPatcher ?: return source
  // the global palette alone: the legacy icon applies it itself
  if (svgPatcher === SVGLoader.colorPatcherProvider) return source
  val target = if (source !is CachedImageIcon && source is RetrievableIcon) source.retrieveIcon() else source
  if (target is CachedImageIcon) {
    return target.createWithPatcher(svgPatcher, isDark = params.isDark, useStroke = params.isStroke)
  }
  val stroke = (imageModifiers as? DefaultImageModifiers)?.stroke
  return if (stroke != null) toStrokeIcon(target, stroke.toAwtColor()) else colorPatchedIcon(target, svgPatcher)
}

/** This icon at the unit scale with the object scale [objScale]: its design size times [objScale]. */
private fun CachedImageIcon.unitScaled(objScale: Double): Icon =
  scale(ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(objScale), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0))))

