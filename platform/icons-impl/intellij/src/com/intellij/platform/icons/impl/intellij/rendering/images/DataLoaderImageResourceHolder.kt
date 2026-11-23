// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.icons.createImageDataLoaderByUrl
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.io.URLUtil
import java.awt.Component
import java.awt.Image
import java.io.IOException
import java.lang.ref.SoftReference
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

internal class DataLoaderImageResourceHolder(
  val dataLoader: ImageDataLoader
) : SwingImageResourceHolder {
  /** The design size of the artwork, as `IntelliJImageResource` sees it (its `width` / `height`). */
  private val designDimensions by lazy { designSize(dataLoader) }

  /**
   * The loaders of the `@WxH` variants, by [variantKey]: [NO_VARIANT] for a variant that does not exist or is not drawn for
   * W×H. A lookup runs on each paint, so a hit and a miss are both kept.
   */
  private val variants = ConcurrentHashMap<Long, Any>()

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
    component: Component?,
  ): Image? {
    val variant = painted?.let { sizeVariant(scale, it, imageModifiers.sizeVariantPolicy()) }
    val (loader, loaderScale) = variant ?: (dataLoader to scale)
    val key = ImageKey(
      loader = loader,
      objScale = loaderScale.getScale(ScaleType.OBJ_SCALE),
      usrScale = loaderScale.getScale(ScaleType.USR_SCALE),
      sysScale = loaderScale.getScale(ScaleType.SYS_SCALE),
      imageModifiers = imageModifiers,
      modificationCount = loader.modificationCount,
    )
    synchronized(images) {
      images[key]?.get()?.let { return it }
    }
    val image = loader.loadImage(parameters = imageModifiers.toLoadParameters(), scaleContext = loaderScale)?.image ?: return null
    synchronized(images) {
      images[key] = SoftReference(image)
    }
    return image
  }

  /** The design size of the image, without the IDE zoom: the renderer applies the zoom, see `SwingIcon`. */
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    return designSize(dataLoader, imageModifiers)
  }

  /**
   * The hand-drawn `@WxH` variant [policy] selects for the logical size [painted], with the scale that draws it into the
   * same device pixels; `null` if none applies (see [SizeVariants]). The loader of the variant resolves its `_dark`
   * and stroke variants, as for the base image.
   */
  private fun sizeVariant(scale: ScaleContext, painted: PaintedSize, policy: SizeVariantPolicy): Pair<ImageDataLoader, ScaleContext>? {
    if (policy == SizeVariantPolicy.None || scale.getScale(ScaleType.OBJ_SCALE) == 1.0) return null
    val design = designDimensions
    val zoom = scale.getScale(ScaleType.USR_SCALE)
    val (width, height) = SizeVariants.chooseVariant(policy, painted, zoom, design.width, design.height) { w, h ->
      variantLoader(w, h) != null
    } ?: return null
    val variant = variantLoader(width, height) ?: return null
    return variant to SizeVariantSupport.variantScale(scale, design.width, width)
  }

  /** The loader of the `@WxH` variant, if it exists and, like the legacy check, is drawn for W×H. */
  private fun variantLoader(width: Int, height: Int): ImageDataLoader? {
    val result = variants.computeIfAbsent(SizeVariants.key(width, height)) {
      val variant = findVariantLoader(width, height)
      if (variant != null && designSize(variant) == Dimensions(width, height)) variant else NO_VARIANT
    }
    return result as? ImageDataLoader
  }

  /**
   * The loader of the `@WxH` variant next to the image. For a class loader resource, it is the effective (theme-patched)
   * path, as `loadIconCustomVersion` uses `CachedImageIcon.getCoords()`. For a `file:` or `jar:` URL, it is the file or
   * archive entry next to the image. Other URLs have no variants: a variant is not looked for on the network.
   */
  private fun findVariantLoader(width: Int, height: Int): ImageDataLoader? {
    dataLoader.getCoords()?.let { (path, classLoader) ->
      val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height) ?: return null
      return findIconLoaderByPath(variantPath, classLoader)
    }
    val url = dataLoader.url?.toExternalForm() ?: return null
    val variantUrl = SizeVariants.variantPath(url, width, height) ?: return null
    if (!localUrlExists(variantUrl)) return null
    return try {
      createImageDataLoaderByUrl(variantUrl)
    }
    catch (_: IllegalArgumentException) {
      null
    }
  }
}

/** Whether the local `file:` URL or `jar:file:` URL [url] names an existing file or archive entry. */
private fun localUrlExists(url: String): Boolean {
  return try {
    when {
      url.startsWith("file:") -> URI(url).let { it.host.isNullOrEmpty() && Files.isRegularFile(Path.of(it)) }
      url.startsWith("jar:file:") -> {
        val separator = url.indexOf("!/").takeIf { it > 0 } ?: return false
        val archive = URI(url.substring("jar:".length, separator))
        val entry = URLUtil.unescapePercentSequences(url.substring(separator + "!/".length))
        // ZipFile, not a JarURLConnection: the connection caches the archive and keeps it open
        archive.host.isNullOrEmpty() && Files.isRegularFile(Path.of(archive)) &&
        ZipFile(Path.of(archive).toFile()).use { it.getEntry(entry) != null }
      }
      else -> false
    }
  }
  catch (_: IOException) {
    false
  }
  catch (_: IllegalArgumentException) {
    false
  }
  catch (_: URISyntaxException) {
    false
  }
}

private val NO_VARIANT = Any()

/** The number of images that a [DataLoaderImageResourceHolder] keeps, as `ScaledIconCache` of `CachedImageIcon` does. */
private const val IMAGE_CACHE_LIMIT = 8

private data class ImageKey(
  val loader: ImageDataLoader,
  val objScale: Double,
  val usrScale: Double,
  val sysScale: Double,
  val imageModifiers: ImageModifiers?,
  val modificationCount: Int,
)

private val UNIT_SCALE: ScaleContext
  get() = ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(1.0), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0)))

private fun designSize(loader: ImageDataLoader, imageModifiers: ImageModifiers? = null): Dimensions {
  val image = loader.loadImage(imageModifiers.toLoadParameters(), UNIT_SCALE)?.image ?: return Dimensions(0, 0)
  return Dimensions(image.getWidth(null), image.getHeight(null))
}
