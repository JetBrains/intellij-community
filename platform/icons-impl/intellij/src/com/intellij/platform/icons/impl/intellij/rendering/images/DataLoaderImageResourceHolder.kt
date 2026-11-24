// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.impl.rendering.SizeVariantCache
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import java.awt.Component
import java.awt.Image
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal class DataLoaderImageResourceHolder(
  val dataLoader: ImageDataLoader
) : SwingImageResourceHolder {
  /**
   * The design sizes of the artwork, by the [Artwork] that the modifiers select, as `IntelliJImageResource` sees them
   * (its `width` / `height`).
   */
  private val designDimensions = ConcurrentHashMap<Artwork, Dimensions>()

  /** The loaders of the `@WxH` variants, by the [Artwork] that the modifiers select. A variant is checked in that artwork. */
  private val variants = ConcurrentHashMap<Artwork, SizeVariantCache<ImageDataLoader>>()

  /**
   * The last [IMAGE_CACHE_LIMIT] rasterized images. The loaders do not cache them, because a `CachedImageIcon` caches its
   * images. Without this cache, each paint rasterizes the SVG again.
   */
  private val images = object : LinkedHashMap<ImageKey, SoftReference<Image>>(IMAGE_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ImageKey, SoftReference<Image>>): Boolean =
      size > IMAGE_CACHE_LIMIT
  }

  override fun getImage(
    scale: ScaleContext,
    painted: PaintedSize?,
    imageModifiers: ImageModifiers?,
    theme: ThemeContext,
    component: Component?,
  ): Image? {
    val variant = painted?.let { sizeVariant(scale, it, imageModifiers) }
    val (loader, loaderScale) = variant ?: (dataLoader to scale)
    // the theme inputs are read now, so the digest of the theme is part of the key
    val key = ImageKey(
      loader = loader,
      objScale = loaderScale.getScale(ScaleType.OBJ_SCALE),
      usrScale = loaderScale.getScale(ScaleType.USR_SCALE),
      sysScale = loaderScale.getScale(ScaleType.SYS_SCALE),
      imageModifiers = imageModifiers,
      themeDigest = theme.digest(),
      modificationCount = loader.modificationCount,
    )
    synchronized(images) {
      images[key]?.get()?.let { return it }
    }
    val image = loader.loadImage(parameters = imageModifiers.toLoadParameters(theme), scaleContext = loaderScale)?.image ?: return null
    synchronized(images) {
      images[key] = SoftReference(image)
    }
    return image
  }

  /** The design size of the image, without the IDE zoom: the renderer applies the zoom, see `SwingIcon`. */
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    return designDimensions(Artwork.of(imageModifiers), imageModifiers)
  }

  private fun designDimensions(artwork: Artwork, imageModifiers: ImageModifiers?): Dimensions =
    designDimensions.computeIfAbsent(artwork) { designSize(dataLoader, imageModifiers) }

  /**
   * The hand-drawn `@WxH` variant that the policy of [imageModifiers] selects for the logical size [painted], with the
   * scale that draws it into the same device pixels; `null` if none applies (see [SizeVariants]). The base and the
   * variant are both measured in the artwork that [imageModifiers] select, for example the dark one. The loader of the
   * variant resolves its `_dark` and stroke variants, as for the base image.
   */
  private fun sizeVariant(scale: ScaleContext, painted: PaintedSize, imageModifiers: ImageModifiers?): Pair<ImageDataLoader, ScaleContext>? {
    val policy = imageModifiers.sizeVariantPolicy()
    if (policy == SizeVariantPolicy.None) return null
    // only a class loader resource has variants, as for legacy icons: `findIconCustomVersion` needs the coordinates
    val (path, classLoader) = dataLoader.getCoords() ?: return null
    val artwork = Artwork.of(imageModifiers)
    val design = designDimensions(artwork, imageModifiers)
    val zoom = scale.getScale(ScaleType.USR_SCALE)
    val cache = variants.computeIfAbsent(artwork) { SizeVariantCache() }
    val variant: (Int, Int) -> ImageDataLoader? = { w, h -> cache.get(w, h) { variantLoader(path, classLoader, w, h, imageModifiers) } }
    val (width, height) = SizeVariants.chooseVariant(policy, painted, zoom, design.width, design.height) { w, h ->
      variant(w, h) != null
    } ?: return null
    val found = variant(width, height) ?: return null
    return found to SizeVariantSupport.variantScale(scale, design.width, width)
  }

  /**
   * The loader of the `@WxH` variant of [path], if it exists and, like the legacy check, is drawn for W×H in the
   * artwork that [imageModifiers] select. [path] is the effective (theme-patched) path, as `loadIconCustomVersion` uses
   * `CachedImageIcon.getCoords()`.
   */
  private fun variantLoader(path: String, classLoader: ClassLoader, width: Int, height: Int, imageModifiers: ImageModifiers?): ImageDataLoader? {
    val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height) ?: return null
    val variant = findIconLoaderByPath(variantPath, classLoader)
    return variant.takeIf { designSize(it, imageModifiers) == Dimensions(width, height) }
  }
}

/**
 * The artwork that the modifiers select: the dark one or the light one, and the stroke one or not. The color patches do
 * not change the size of the artwork.
 */
private data class Artwork(val isDark: Boolean, val isStroke: Boolean) {
  companion object {
    fun of(imageModifiers: ImageModifiers?): Artwork =
      imageModifiers.toLoadParameters(rootPatcher = null, globalFilter = null).let { Artwork(it.isDark, it.isStroke) }
  }
}

/** The number of images that a [DataLoaderImageResourceHolder] keeps, as `ScaledIconCache` of `CachedImageIcon` does. */
private const val IMAGE_CACHE_LIMIT = 8

private data class ImageKey(
  val loader: ImageDataLoader,
  val objScale: Double,
  val usrScale: Double,
  val sysScale: Double,
  val imageModifiers: ImageModifiers?,
  val themeDigest: String,
  val modificationCount: Int,
)
private val UNIT_SCALE: ScaleContext
  get() = ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(1.0), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0)))

/**
 * The size of the image that [loader] loads with [imageModifiers] at the unit scale. The modifiers can select an artwork
 * of another size, for example the dark one. The theme inputs do not change the size, so they are not applied.
 */
private fun designSize(loader: ImageDataLoader, imageModifiers: ImageModifiers?): Dimensions {
  val image = loader.loadImage(imageModifiers.toLoadParameters(rootPatcher = null, globalFilter = null), UNIT_SCALE)?.image
    ?: return Dimensions(0, 0)
  return Dimensions(image.getWidth(null), image.getHeight(null))
}
