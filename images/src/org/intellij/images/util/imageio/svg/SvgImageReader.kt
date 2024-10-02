// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import com.intellij.ui.components.impl.SvgImageDecoder
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.stream.ImageInputStream

class SvgImageReader(svgImageReaderSpi: SvgImageReaderSpi) : ImageReader(svgImageReaderSpi) {
  private var jSvgDocument: SvgImageDecoder.SvgDocument? = null

  override fun setInput(input: Any?, seekForwardOnly: Boolean, ignoreMetadata: Boolean) {
    super.setInput(input, seekForwardOnly, ignoreMetadata)
    reset()
  }

  override fun getWidth(imageIndex: Int): Int {
    loadInfoIfNeeded()
    return (jSvgDocument?.width?.toInt() ?: throw IOException("SVG document not loaded"))
  }

  override fun getHeight(imageIndex: Int): Int {
    loadInfoIfNeeded()
    return (jSvgDocument?.height?.toInt() ?: throw IOException("SVG document not loaded"))
  }

  @Throws(IOException::class)
  private fun loadInfoIfNeeded() {
    val input = input
    if (jSvgDocument == null && input is ImageInputStream) {

      try {
        // Not using ByteArray to avoid potential humongous allocation
        ImageInputStreamAdapter(input).buffered().use { bufferedInput ->
          jSvgDocument = SvgImageDecoder.createSvgDocument(bufferedInput)
        }
      }
      catch (e: IOException) {
        // could not read the SVG document
      }
    }
  }

  @Throws(IOException::class)
  override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage {
    loadInfoIfNeeded()
    val jSvgDocument = jSvgDocument ?: throw IOException("SVG document not loaded")

    val sourceRenderSize = param?.sourceRenderSize

    return jSvgDocument.createImage(sourceRenderSize?.width?.takeIf { it > 0 },
                                    sourceRenderSize?.height?.takeIf { it > 0 })
  }

  override fun reset() {
    jSvgDocument = null
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