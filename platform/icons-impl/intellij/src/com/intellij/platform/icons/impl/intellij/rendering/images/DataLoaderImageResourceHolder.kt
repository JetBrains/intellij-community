// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import java.awt.Image

internal class DataLoaderImageResourceHolder(
  val dataLoader: ImageDataLoader
) : SwingImageResourceHolder {
  /** Size of the image as `IntelliJImageResource` sees it (its `width` / `height`). */
  private val ownDimensions by lazy { getExpectedDimensions() }

  /** Design size of the artwork: rasterized with a unit scale context, independent of the environment. */
  private val designDimensions by lazy { designSize(dataLoader) }

  override fun getImage(
    scale: ScaleContext,
    imageModifiers: ImageModifiers?,
    theme: ThemeContext,
  ): Image? {
    val (loader, loaderScale) = sizeVariant(scale, imageModifiers.sizeVariantPolicy()) ?: (dataLoader to scale)
    return loader.loadImage(
      parameters = imageModifiers.toLoadParameters(theme),
      scaleContext = loaderScale
    )?.image
  }

  override fun getExpectedDimensions(): Dimensions {
    val img = dataLoader.loadImage(NO_THEME_PARAMETERS, ScaleContext.create())?.image ?: return Dimensions(0, 0)
    return Dimensions(img.getWidth(null), img.getHeight(null))
  }

  /**
   * The hand-drawn `@WxH` variant [policy] selects for the logical size [scale] paints this image at, with the scale that
   * draws it into the same box; `null` if none applies (see [SizeVariantSupport]). `_dark` / stroke variants of it are
   * resolved by its loader, as for the base image.
   */
  private fun sizeVariant(scale: ScaleContext, policy: SizeVariantPolicy): Pair<ImageDataLoader, ScaleContext>? {
    if (policy == SizeVariantPolicy.None || scale.getScale(ScaleType.OBJ_SCALE) == 1.0) return null
    val expected = ownDimensions
    val design = designDimensions
    if (expected.width <= 0 || expected.height <= 0 || design.width <= 0 || design.height <= 0) return null
    val logicalWidth = SizeVariantSupport.logicalSize(expected.width, scale)
    val logicalHeight = SizeVariantSupport.logicalSize(expected.height, scale)
    // the effective (theme-patched) path, as `loadIconCustomVersion` uses `CachedImageIcon.getCoords()`
    val (path, classLoader) = dataLoader.getCoords() ?: return null
    val (width, height) = SizeVariantSupport.chooseVariant(
      policy, logicalWidth, logicalHeight, JBUIScale.scale(design.width), JBUIScale.scale(design.height), design.width, design.height,
    ) { w, h -> SizeVariantSupport.existingVariantPath(path, classLoader, w, h) != null } ?: return null
    val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height) ?: return null
    val variant = findIconLoaderByPath(variantPath, classLoader)
    // like the legacy check, accept the variant only if it really is drawn for W×H
    val variantDesign = designSize(variant)
    if (variantDesign.width != width || variantDesign.height != height) return null
    return variant to SizeVariantSupport.variantScale(scale, logicalWidth, width)
  }
}

/** The size of an image does not depend on the theme, so the size checks load it without the theme inputs. */
private val NO_THEME_PARAMETERS
  get() = null.toLoadParameters(rootPatcher = null, globalFilter = null)

private val UNIT_SCALE: ScaleContext
  get() = ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(1.0), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0)))

private fun designSize(loader: ImageDataLoader): Dimensions {
  val image = loader.loadImage(NO_THEME_PARAMETERS, UNIT_SCALE)?.image ?: return Dimensions(0, 0)
  return Dimensions(image.getWidth(null), image.getHeight(null))
}
