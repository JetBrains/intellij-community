// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.geometry.size.Length
import com.github.weisj.jsvg.nodes.SVG
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.createJSvgDocument
import com.intellij.util.ui.ImageUtil
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.stream.ImageInputStream
import kotlin.math.max

class SvgImageReader(svgImageReaderSpi: SvgImageReaderSpi) : ImageReader(svgImageReaderSpi) {
  private var height: Length? = null
  private var width: Length? = null
  private var jSvgDocument: SVG? = null
  override fun setInput(input: Any?, seekForwardOnly: Boolean, ignoreMetadata: Boolean) {
    super.setInput(input, seekForwardOnly, ignoreMetadata)
    reset()
  }

  override fun getWidth(imageIndex: Int): Int {
    loadInfoIfNeeded()
    return width?.raw()?.toInt() ?: throw IOException("SVG document not loaded")
  }

  override fun getHeight(imageIndex: Int): Int {
    loadInfoIfNeeded()
    return height?.raw()?.toInt() ?: throw IOException("SVG document not loaded")
  }

  @Throws(IOException::class)
  private fun loadInfoIfNeeded() {
    val input = input
    if (jSvgDocument == null && input is ImageInputStream) {

      try {
        // Not using ByteArray to avoid potential humongous allocation
        ImageInputStreamAdapter(input).buffered().use {
          jSvgDocument = createJSvgDocument(it).also { svg ->
            width = svg.width
            height = svg.height
          }
        }
      } catch (e: IOException) {
        // could not read the SVG document
      }
    }
  }

  @Throws(IOException::class)
  override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage {
    loadInfoIfNeeded()
    jSvgDocument ?: throw IOException("SVG document not loaded")

    val sourceRenderSize = param?.sourceRenderSize

    val width =  sourceRenderSize?.width?.toDouble() ?: width!!.raw().toDouble()
    val height = sourceRenderSize?.height?.toDouble() ?: height!!.raw().toDouble()

    // how to have an hidpi aware image?
    val bi = ImageUtil.createImage(
      ScaleContext.create(),
      width,
      height,
      BufferedImage.TYPE_INT_ARGB,
      PaintUtil.RoundingMode.ROUND
    )
    val g = bi.createGraphics()

    ImageUtil.applyQualityRenderingHints(g)

    jSvgDocument?.renderWithSize(
      width.toFloat(),
      height.toFloat(),
      SVGFont.defaultFontSize(),
      g,
    )
    return bi
  }

  override fun reset() {
    jSvgDocument = null
    height = null
    width = null
  }

  override fun dispose() {
    reset()
  }

  override fun getNumImages(allowSearch: Boolean): Int {
    return if (jSvgDocument != null) 1 else 0
  }

  override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> {
    return listOf<ImageTypeSpecifier>(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()
  }

  override fun getStreamMetadata() = null

  override fun getImageMetadata(imageIndex: Int) = null

}

private class ImageInputStreamAdapter(private val imageInputStream: ImageInputStream) : InputStream() {
  private var closed = false

  @Throws(IOException::class)
  override fun close() {
    closed = true
  }

  @Throws(IOException::class)
  override fun read(): Int {
    if(closed) throw IOException("stream closed")
    return imageInputStream.read()
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if(closed) throw IOException("stream closed")
    if (len <= 0) {
      return 0
    }
    return imageInputStream.read(b, off, max(len.toLong(), 0).toInt())
  }

  @Throws(IOException::class)
  override fun skip(n: Long): Long {
    if(closed) throw IOException("stream closed")
    if (n <= 0) {
      return 0
    }
    return imageInputStream.skipBytes(n)
  }
}