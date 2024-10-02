// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import com.intellij.ui.components.impl.SvgImageDecoder
import java.util.*
import javax.imageio.ImageReader
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream

/**
 * ImageReaderSpi for SVG images.
 *
 * @author Brice Dutheil
 */
class SvgImageReaderSpi : ImageReaderSpi() {
  init {
    vendorName = "weisj/jsvg & JetBrains"
    suffixes = arrayOf("svg")
    MIMETypes = arrayOf("image/svg+xml")
    names = arrayOf("SVG Image Reader")
    pluginClassName = SvgImageReaderSpi::class.java.name
    inputTypes = arrayOf<Class<*>>(ImageInputStream::class.java)
  }

  override fun getDescription(locale: Locale?) = "SVG Image Reader"

  override fun canDecodeInput(source: Any?): Boolean {
    if (source !is ImageInputStream) return false
    return SvgImageDecoder.isSvgDocument(ImageInputStreamAdapter(source))
  }

  override fun createReaderInstance(extension: Any?): ImageReader {
    return SvgImageReader(this)
  }
}

