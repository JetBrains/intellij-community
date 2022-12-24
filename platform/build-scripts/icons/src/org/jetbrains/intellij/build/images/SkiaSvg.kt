// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import org.jetbrains.skia.*
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit
import java.nio.file.Path

internal fun renderSvgUsingSkia(file: Path, scale: Float): Bitmap {
  val svg = SVGDOM(Data.makeFromFileName(file.toString()))
  val root = svg.root!!
  val width = svgLengthToPixel(root.width)
  val height = svgLengthToPixel(root.height)
  check(width > 0)
  check(height > 0)

  val bmp = Bitmap()
  bmp.allocPixels(ImageInfo.makeS32((width * scale).toInt(), (height * scale).toInt(), ColorAlphaType.UNPREMUL))
  val canvas = Canvas(bmp)
  canvas.scale(scale, scale)
  svg.render(canvas)

  check(bmp.width > 0)
  check(bmp.height > 0)
  return bmp
}

private fun svgLengthToPixel(root: SVGLength): Float {
  return when (root.unit) {
    SVGLengthUnit.PERCENTAGE -> (root.value * 16f) / 100f
    SVGLengthUnit.NUMBER, SVGLengthUnit.PX -> root.value
    else -> {
      throw UnsupportedOperationException(root.toString())
    }
  }
}