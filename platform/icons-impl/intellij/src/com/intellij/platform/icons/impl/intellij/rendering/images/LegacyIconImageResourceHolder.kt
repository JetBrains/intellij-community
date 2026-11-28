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
import com.intellij.platform.icons.rendering.ThemeContext
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
   * logical size. It is the size of the artwork that [getImage] paints for [imageModifiers], see [measuredIn].
   */
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    val size = legacySize(backingLegacyIcon.measuredIn(imageModifiers))
    return Dimensions(size.width, size.height)
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
    val (source, sourceScale) = painted?.let { sizeVariant(scale, it, imageModifiers) } ?: (backingLegacyIcon to withOwnScale(scale))
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
   * The hand-drawn `@WxH` variant that the policy of [imageModifiers] selects for a backing `CachedImageIcon` at the logical size [painted] (see
   * [SizeVariants]), carrying the original icon's modifications (dark state, filters, patcher) as legacy
   * `loadIconCustomVersion` does via `withAnotherIconModifications`; `null` if none applies.
   */
  private fun sizeVariant(scale: ScaleContext, painted: PaintedSize, imageModifiers: ImageModifiers?): Pair<Icon, ScaleContext>? {
    val policy = imageModifiers.sizeVariantPolicy()
    if (policy == SizeVariantPolicy.None) return null
    val cached = backingLegacyIcon.cachedImageIcon() ?: return null
    // the renderer computes the painted size from the measured size, see [getExpectedDimensions]
    val design = getExpectedDimensions(imageModifiers)
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

/**
 * This legacy icon in the artwork that [imageModifiers] select, as `LegacyIconImageResourceHolder.getImage` selects it.
 * An explicit dark state can select an artwork of another size. A stroked icon loads the light artwork. The color patches
 * and the filters do not change the size, so they are not applied.
 */
internal fun Icon.measuredIn(imageModifiers: ImageModifiers?): Icon {
  val defaultModifiers = imageModifiers as? DefaultImageModifiers
  val forcedDark = if (defaultModifiers?.stroke != null) false else defaultModifiers?.forcedDark ?: return this
  return IconLoader.getDarkIcon(this, forcedDark)
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

