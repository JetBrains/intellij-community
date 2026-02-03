// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import com.intellij.ui.svg.JSvgDocument
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.stream.ImageInputStream

internal class SvgImageReader(svgImageReaderSpi: SvgImageReaderSpi) : ImageReader(svgImageReaderSpi) {
  private var _jSvgDocument: JSvgDocument? = null

  override fun setInput(input: Any?, seekForwardOnly: Boolean, ignoreMetadata: Boolean) {
    super.setInput(input, seekForwardOnly, ignoreMetadata)
    reset()
  }

  override fun getWidth(imageIndex: Int): Int {
    return jSvgDocument.width.toInt()
  }

  override fun getHeight(imageIndex: Int): Int {
    return jSvgDocument.height.toInt()
  }

  private val jSvgDocument: JSvgDocument
    get() {
      val input = input
      if (_jSvgDocument == null && input is ImageInputStream) {
        try {
          // Not using ByteArray to avoid potential humongous allocation
          ImageInputStreamAdapter(input).buffered().use { bufferedInput ->
            _jSvgDocument = JSvgDocument.create(bufferedInput)
          }
        }
        catch (e: Exception) {
          throw IOException("SVG document not loaded", e)
        }
      }
      else
        throw IOException("Unsupported input stream class: ${input.javaClass}")
      return _jSvgDocument!!
    }

  @Throws(IOException::class)
  override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage {
    val sourceRenderSize = param?.sourceRenderSize
    return jSvgDocument.createImage(sourceRenderSize?.width?.takeIf { it > 0 },
                                    sourceRenderSize?.height?.takeIf { it > 0 })
  }

  override fun reset() {
    _jSvgDocument = null
  }

  override fun dispose() {
    reset()
  }

  override fun getNumImages(allowSearch: Boolean): Int {
    return if (_jSvgDocument != null) 1 else 0
  }

  override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> {
    return listOf<ImageTypeSpecifier>(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()
  }

  override fun getStreamMetadata() = null

  override fun getImageMetadata(imageIndex: Int) = null

}