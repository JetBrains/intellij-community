// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ImageDataByUrlLoader
import com.intellij.openapi.util.Pair
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.IconTransform
import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.SVGLoader
import com.intellij.util.ui.StartupUiUtil
import org.intellij.lang.annotations.MagicConstant
import java.awt.Image
import java.awt.image.ImageFilter
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL

internal class RasterizedImageDataLoader(private val path: String,
                                         private val classLoaderRef: WeakReference<ClassLoader>,
                                         private val originalPath: String,
                                         private val originalClassLoaderRef: WeakReference<ClassLoader>,
                                         private val cacheKey: Int,
                                         private val imageFlags: Int) : ImageDataLoader {
  override fun loadImage(filters: List<ImageFilter?>, scaleContext: ScaleContext, isDark: Boolean): Image? {
    val classLoader = classLoaderRef.get() ?: return null

    // do not use cache
    var flags = ImageLoader.ALLOW_FLOAT_SCALING
    if (isDark) {
      flags = flags or ImageLoader.USE_DARK
    }

    // use cache key only if path to image is not customized
    try {
      if (originalPath === path) {
        val isSvg = cacheKey != 0
        // use cache key only if path to image is not customized
        return loadRasterized(path = path,
                              filters = filters,
                              classLoader = classLoader,
                              flags = flags,
                              scaleContext = scaleContext,
                              isSvg = isSvg,
                              rasterizedCacheKey = cacheKey,
                              imageFlags = imageFlags,
                              patched = false)
      }
      else {
        val isSvg = path.endsWith(".svg")
        return loadRasterized(path = path,
                              filters = filters,
                              classLoader = classLoader,
                              flags = flags,
                              scaleContext = scaleContext,
                              isSvg = isSvg,
                              rasterizedCacheKey = 0,
                              imageFlags = imageFlags,
                              patched = true)
      }
    }
    catch (e: IOException) {
      logger<RasterizedImageDataLoader>().debug(e)
      return null
    }
  }

  override fun getURL() = classLoaderRef.get()?.getResource(path)

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    val classLoader = classLoaderRef.get()
    val patched = transform.patchPath(originalPath, classLoader)
                  ?: return if (path !== this.originalPath && this.originalPath == normalizePath(originalPath)) {
                    RasterizedImageDataLoader(path = this.originalPath,
                                              classLoaderRef = originalClassLoaderRef,
                                              originalPath = this.originalPath,
                                              originalClassLoaderRef = originalClassLoaderRef,
                                              cacheKey = cacheKey,
                                              imageFlags = imageFlags)
                  }
                  else null
    if (patched.first.startsWith("file:/")) {
      val effectiveClassLoader = patched.second ?: classLoader
      return ImageDataByUrlLoader(URL(patched.first), patched.first, effectiveClassLoader, false)
    }
    else {
      return createPatched(this.originalPath, originalClassLoaderRef, patched, cacheKey, imageFlags)
    }
  }

  override fun isMyClassLoader(classLoader: ClassLoader) = classLoaderRef.get() === classLoader

  override fun toString() = "RasterizedImageDataLoader(classLoader=${classLoaderRef.get()}, path=$path)"
}

internal fun createPatched(originalPath: String,
                          originalClassLoaderRef: WeakReference<ClassLoader>,
                          patched: Pair<String, ClassLoader?>,
                          cacheKey: Int,
                          imageFlags: Int): ImageDataLoader {
  val effectivePath = normalizePath(patched.first)
  val effectiveClassLoaderRef: WeakReference<ClassLoader> = patched.second?.let(::WeakReference) ?: originalClassLoaderRef
  return RasterizedImageDataLoader(path = effectivePath,
                                   classLoaderRef = effectiveClassLoaderRef,
                                   originalPath = originalPath,
                                   originalClassLoaderRef = originalClassLoaderRef,
                                   cacheKey = cacheKey,
                                   imageFlags = imageFlags)
}

private fun normalizePath(patchedPath: String): String {
  return patchedPath.removePrefix("")
}

@Throws(IOException::class)
private fun loadRasterized(path: String,
                           filters: List<ImageFilter?>,
                           classLoader: ClassLoader,
                           @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
                           scaleContext: ScaleContext,
                           isSvg: Boolean,
                           rasterizedCacheKey: Int,
                           @MagicConstant(flagsFromClass = ImageDescriptor::class) imageFlags: Int,
                           patched: Boolean): Image? {
  val loadingStart = StartUpMeasurer.getCurrentTimeIfEnabled()

  // Prefer retina images for HiDPI scale, because downscaling
  // retina images provides a better result than up-scaling non-retina images.
  val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val dotIndex = path.lastIndexOf('.')
  val name = if (dotIndex < 0) path else path.substring(0, dotIndex)
  val scale = ImageLoader.adjustScaleFactor(flags and ImageLoader.ALLOW_FLOAT_SCALING == ImageLoader.ALLOW_FLOAT_SCALING, pixScale)
  val isDark = flags and ImageLoader.USE_DARK == ImageLoader.USE_DARK
  val isRetina = JBUIScale.isHiDPI(pixScale.toDouble())
  val imageScale: Float
  val ext = if (isSvg) "svg" else if (dotIndex < 0 || dotIndex == path.length - 1) "" else path.substring(dotIndex + 1)
  val effectivePath: String
  var isEffectiveDark = isDark
  if (isRetina && isDark && imageFlags and ImageDescriptor.HAS_DARK_2x == ImageDescriptor.HAS_DARK_2x) {
    effectivePath = "$name@2x_dark.$ext"
    imageScale = if (isSvg) scale else 2f
  }
  else if (isDark && imageFlags and ImageDescriptor.HAS_DARK == ImageDescriptor.HAS_DARK) {
    effectivePath = "${name}_dark.$ext"
    imageScale = if (isSvg) scale else 1f
  }
  else {
    isEffectiveDark = false
    if (isRetina && imageFlags and ImageDescriptor.HAS_2x == ImageDescriptor.HAS_2x) {
      effectivePath = "$name@2x.$ext"
      imageScale = if (isSvg) scale else 2f
    }
    else {
      effectivePath = path
      imageScale = if (isSvg) scale else 1f
    }
  }

  // todo remove it, not used
  val originalUserSize = ImageLoader.Dimension2DDouble(0.0, 0.0)
  if (patched) {
    val retinaDark = Pair("$name@2x_dark.$ext", if (isSvg) scale else 2f)
    val dark = Pair(name + "_dark." + ext, if (isSvg) scale else 1f)
    val retina = Pair("$name@2x.$ext", if (isSvg) scale else 2f)
    val plain = Pair(path, if (isSvg) scale else 1f)
    val effectivePaths = if (isRetina && isDark) {
      listOf(retinaDark, dark, retina, plain)
    }
    else if (isDark) {
      listOf(dark, plain)
    }
    else {
      if (isRetina) listOf(retina, plain) else listOf(plain)
    }
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    var image: Image? = null
    var isUpScaleNeeded = !isSvg
    for (effPath in effectivePaths) {
      val pathToImage = effPath.first
      val imgScale = effPath.second
      image = if (isSvg) {
        SVGLoader.loadFromClassResource(null, classLoader, pathToImage, rasterizedCacheKey, imgScale, isEffectiveDark,
                                        originalUserSize)
      }
      else {
        ImageLoader.loadPngFromClassResource(pathToImage, null, classLoader, imgScale.toDouble(), originalUserSize)
      }
      if (image != null) {
        if (isUpScaleNeeded) {
          isUpScaleNeeded = effPath === plain || effPath === dark
        }
        break
      }
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromResources.end(start)
      IconLoadMeasurer.addLoading(isSvg, loadingStart)
    }
    return if (image == null) {
      null
    }
    else ImageLoader.convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, StartupUiUtil.isJreHiDPI(scaleContext),
                                  imageScale.toDouble(), isSvg)
  }
  else {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val image = if (isSvg) {
      SVGLoader.loadFromClassResource(null, classLoader, effectivePath, rasterizedCacheKey, imageScale, isEffectiveDark,
                                      originalUserSize)
    }
    else {
      ImageLoader.loadPngFromClassResource(effectivePath, null, classLoader, imageScale.toDouble(), originalUserSize)
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromResources.end(start)
      IconLoadMeasurer.addLoading(isSvg, loadingStart)
    }
    return if (image == null) {
      null
    }
    else ImageLoader.convertImage(image, filters, flags, scaleContext, !isSvg, StartupUiUtil.isJreHiDPI(scaleContext),
                                  imageScale.toDouble(), isSvg)
  }
}