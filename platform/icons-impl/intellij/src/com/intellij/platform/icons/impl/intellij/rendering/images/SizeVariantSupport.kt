// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType

/** The Swing part of `@WxH` size variants: the class loader resources and the scale. The choice is in [SizeVariants]. */
internal object SizeVariantSupport {
  /** `<path minus .svg>@WxH.svg` if [path] is an SVG and that resource exists in [classLoader], else `null`. */
  fun existingVariantPath(path: String, classLoader: ClassLoader, width: Int, height: Int): String? {
    val variant = SizeVariants.variantPath(path, width, height) ?: return null
    return if (classLoader.getResource(variant.removePrefix("/")) != null) variant else null
  }

  /**
   * [scale] with the object scale that draws a variant of the design width [variantWidth] into the same device pixels as
   * the base artwork of the design width [baseDesignWidth].
   */
  fun variantScale(scale: ScaleContext, baseDesignWidth: Int, variantWidth: Int): ScaleContext {
    return ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(scale.getScale(ScaleType.OBJ_SCALE) * baseDesignWidth / variantWidth),
      ScaleType.USR_SCALE.of(scale.getScale(ScaleType.USR_SCALE)),
      ScaleType.SYS_SCALE.of(scale.getScale(ScaleType.SYS_SCALE)),
    ))
  }
}
