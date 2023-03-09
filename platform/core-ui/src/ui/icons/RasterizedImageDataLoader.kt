// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheMapper
import com.intellij.util.ImageLoader
import com.intellij.util.SVGLoader
import com.intellij.util.ui.StartupUiUtil
import org.intellij.lang.annotations.MagicConstant
import java.awt.Image
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL

internal class RasterizedImageDataLoader(private val path: String,
                                         private val classLoaderRef: WeakReference<ClassLoader>,
                                         private val originalPath: String,
                                         private val originalClassLoaderRef: WeakReference<ClassLoader>,
                                         private val cacheKey: Int,
                                         override val flags: Int) : ImageDataLoader {
  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    val classLoader = classLoaderRef.get() ?: return null
    // use the cache key only if a path to image is not customized
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val isSvg: Boolean
      val image = if (originalPath === path) {
        // use the cache key only if a path to image is not customized
        isSvg = cacheKey != 0
        loadRasterized(path = path,
                       scaleContext = scaleContext,
                       parameters = parameters,
                       classLoader = classLoader,
                       isSvg = isSvg,
                       rasterizedCacheKey = cacheKey,
                       imageFlags = flags,
                       isPatched = false)
      }
      else {
        isSvg = path.endsWith(".svg")
        loadRasterized(path = path,
                       scaleContext = scaleContext,
                       parameters = parameters,
                       classLoader = classLoader,
                       isSvg = isSvg,
                       rasterizedCacheKey = 0,
                       imageFlags = flags,
                       isPatched = true)
      }

      if (start != -1L) {
        IconLoadMeasurer.loadFromResources.end(start)
        IconLoadMeasurer.addLoading(isSvg, start)
      }
      return image
    }
    catch (e: IOException) {
      logger<RasterizedImageDataLoader>().debug(e)
      return null
    }
  }

  override val url: URL?
    get() = classLoaderRef.get()?.getResource(path)

  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? {
    val classLoader = classLoaderRef.get()
    val patched = transform.patchPath(originalPath, classLoader)
                  ?: return if (path !== this.originalPath && this.originalPath == normalizePath(originalPath)) {
                    RasterizedImageDataLoader(path = this.originalPath,
                                              classLoaderRef = originalClassLoaderRef,
                                              originalPath = this.originalPath,
                                              originalClassLoaderRef = originalClassLoaderRef,
                                              cacheKey = cacheKey,
                                              flags = flags)
                  }
                  else null
    if (patched.first.startsWith("file:/")) {
      val effectiveClassLoader = patched.second ?: classLoader
      return ImageDataByUrlLoader(url = URL(patched.first), path = patched.first, classLoader = effectiveClassLoader)
    }
    else {
      return createPatched(this.originalPath, originalClassLoaderRef, patched, cacheKey, flags)
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
  val effectiveClassLoaderRef = patched.second?.let(::WeakReference) ?: originalClassLoaderRef
  return RasterizedImageDataLoader(path = effectivePath,
                                   classLoaderRef = effectiveClassLoaderRef,
                                   originalPath = originalPath,
                                   originalClassLoaderRef = originalClassLoaderRef,
                                   cacheKey = cacheKey,
                                   flags = imageFlags)
}

private fun normalizePath(patchedPath: String): String {
  return patchedPath.removePrefix("")
}

private fun loadRasterized(path: String,
                           scaleContext: ScaleContext,
                           parameters: LoadIconParameters,
                           classLoader: ClassLoader,
                           isSvg: Boolean,
                           rasterizedCacheKey: Int,
                           @MagicConstant(flagsFromClass = ImageDescriptor::class) imageFlags: Int,
                           isPatched: Boolean): Image? {
  val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val dotIndex = path.lastIndexOf('.')
  val name = if (dotIndex < 0) path else path.substring(0, dotIndex)
  val isRetina = scale != 1f
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  val ext = if (isSvg) "svg" else if (dotIndex < 0 || dotIndex == path.length - 1) "" else path.substring(dotIndex + 1)
  if (isPatched) {
    return loadPatched(name = name,
                       ext = ext,
                       isSvg = isSvg,
                       scale = scale,
                       scaleContext = scaleContext,
                       parameters = parameters,
                       path = path,
                       isRetina = isRetina,
                       classLoader = classLoader,
                       isEffectiveDark = parameters.isDark)
  }

  var isEffectiveDark = parameters.isDark
  val effectivePath: String
  val nonSvgScale: Float
  if (parameters.isStroke && (imageFlags and ImageDescriptor.HAS_STROKE) == ImageDescriptor.HAS_STROKE) {
    effectivePath = "${name}_scale.$ext"
    nonSvgScale = 1f
  }
  else if (isRetina && parameters.isDark && (imageFlags and ImageDescriptor.HAS_DARK_2x) == ImageDescriptor.HAS_DARK_2x) {
    effectivePath = "$name@2x_dark.$ext"
    nonSvgScale = 2f
  }
  else if (parameters.isDark && imageFlags and ImageDescriptor.HAS_DARK == ImageDescriptor.HAS_DARK) {
    effectivePath = "${name}_dark.$ext"
    nonSvgScale = 1f
  }
  else {
    isEffectiveDark = false
    if (isRetina && imageFlags and ImageDescriptor.HAS_2x == ImageDescriptor.HAS_2x) {
      effectivePath = "$name@2x.$ext"
      nonSvgScale = 2f
    }
    else {
      effectivePath = path
      nonSvgScale = 1f
    }
  }
  val image = if (isSvg) {
    SVGLoader.loadFromClassResource(
      classLoader = classLoader,
      path = effectivePath,
      precomputedCacheKey = rasterizedCacheKey,
      scale = scale,
      compoundCacheKey = SvgCacheMapper(scale = scale, isDark = isEffectiveDark, isStroke = parameters.isStroke),
      colorPatcherProvider = parameters.colorPatcher,
    )
  }
  else {
    ImageLoader.loadPngFromClassResource(path = effectivePath, classLoader = classLoader, scale = nonSvgScale)
  }

  return ImageLoader.convertImage(image = image ?: return null,
                                  filters = parameters.filters,
                                  scaleContext = scaleContext,
                                  isUpScaleNeeded = !isSvg,
                                  isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext),
                                  imageScale = nonSvgScale,
                                  isSvg = isSvg)
}

private class PatchedIconDescriptor(@JvmField val name: String, @JvmField val scale: Float)

private fun loadPatched(name: String,
                        ext: String,
                        isSvg: Boolean,
                        scale: Float,
                        scaleContext: ScaleContext,
                        parameters: LoadIconParameters,
                        path: String,
                        isRetina: Boolean,
                        classLoader: ClassLoader,
                        isEffectiveDark: Boolean): Image? {
  val stroke = PatchedIconDescriptor("${name}_stroke.$ext", if (isSvg) scale else 1f)
  val retinaDark = PatchedIconDescriptor("$name@2x_dark.$ext", if (isSvg) scale else 2f)
  val dark = PatchedIconDescriptor("${name}_dark.$ext", if (isSvg) scale else 1f)
  val retina = PatchedIconDescriptor("$name@2x.$ext", if (isSvg) scale else 2f)
  val plain = PatchedIconDescriptor(path, if (isSvg) scale else 1f)
  val descriptors = when {
    parameters.isStroke -> listOf(stroke, plain)
    isRetina && parameters.isDark -> listOf(retinaDark, dark, retina, plain)
    parameters.isDark -> listOf(dark, plain)
    else -> if (isRetina) listOf(retina, plain) else listOf(plain)
  }

  for (descriptor in descriptors) {
    val image = if (isSvg) {
      SVGLoader.loadFromClassResource(classLoader = classLoader,
                                      path = descriptor.name,
                                      precomputedCacheKey = 0,
                                      scale = descriptor.scale,
                                      compoundCacheKey = SvgCacheMapper(scale = descriptor.scale,
                                                                        isDark = isEffectiveDark,
                                                                        isStroke = parameters.isStroke),
                                      colorPatcherProvider = parameters.colorPatcher)
    }
    else {
      ImageLoader.loadPngFromClassResource(path = descriptor.name, classLoader = classLoader, scale = descriptor.scale)
    }

    if (image != null) {
      val isUpScaleNeeded = !isSvg && (descriptor === plain || descriptor === dark)
      return ImageLoader.convertImage(image = image,
                                      filters = parameters.filters,
                                      scaleContext = scaleContext,
                                      isUpScaleNeeded = isUpScaleNeeded,
                                      isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext),
                                      imageScale = descriptor.scale,
                                      isSvg = isSvg)
    }
  }
  return null
}
