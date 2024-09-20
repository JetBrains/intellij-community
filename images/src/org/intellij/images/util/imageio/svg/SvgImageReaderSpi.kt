// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import java.io.EOFException
import java.util.*
import javax.imageio.ImageReader
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream
import kotlin.experimental.and

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
    return canDecode(source)
  }

  private fun canDecode(imageInputStream: ImageInputStream): Boolean {
    // NOTE: This test is quite quick as it does not involve any parsing,
    // however, it may not recognize all kinds of SVG documents.

    try {
      // We need to read the first few bytes to determine if this is an SVG file,
      // then reset the stream at the marked position
      imageInputStream.mark()

      // SVG file can starts with an XML declaration, then possibly followed by comments, whitespaces
      // \w.*(<?xml version="1.0" encoding="UTF-8" ?>)?
      // (\w|(<!--.*-->))*

      // Then either the doctype gives the hint possibly surrounded by comments and/or whitespaces
      // <!DOCTYPE svg ...
      // (\w|(<!--.*-->))*

      // Or the root tag is svg, possibly preceding comments and/or whitespaces
      // <svg ...

      // Handles first whitespaces if any
      val lastReadByte = imageInputStream.readFirstAfterWhitespaces()

      // The next byte should be '<' (comments, doctype XML declaration or the root tag)
      if (lastReadByte != '<'.code) {
        return false
      }

      val window = ByteArray(4)
      while (true) {
        imageInputStream.readFully(window)

        when {
          // `<?` Handles the XML declaration
          window.startsWith('?') -> imageInputStream.skipUntil('?', '>')
          //buffer[0] == '?'.code.toByte() -> imageInputStream.skipUntil('?', '>')

          // `<!--` Handles a comment
          window.startsWith('!', '-', '-') -> imageInputStream.skipUntil('-', '-', '>')

          // `<!DOCTYPE` Handles the DOCTYPE declaration
          window.startsWith('!', 'D', 'O', 'C') && imageInputStream.readNextEquals(charArrayOf('T', 'Y', 'P', 'E')) -> {
            val lastReadChar = imageInputStream.readFirstAfterWhitespaces()
            return lastReadChar == 's'.code && imageInputStream.readNextEquals(charArrayOf('v', 'g'))
          }

          // `<svg` Handles the root tag
          window.startsWith('s', 'v', 'g') && (Char(window[3].toUShort()).isWhitespace() || window[3] == ':'.code.toByte()) -> {
            return true
          }

          // not an SVG file or not handled
          else -> return false
        }

        //while ((imageInputStream.readByte() and 0xFF.toByte()).toInt().toChar() != '<') {
        //  // Skip over, until next begin tag or EOF
        //}
        // Skip over, until next begin tag or EOF
        imageInputStream.skipUntil('<')
      }
    } catch (ignore: EOFException) {
      // Possible for small files...
      ignore.printStackTrace()
      return false
    } finally {
      imageInputStream.reset()
    }
  }

  private fun ImageInputStream.readFirstAfterWhitespaces(): Int {
    var lastReadByte: Int
    while ((read().also { lastReadByte = it }).toChar().isWhitespace()) {
      // skip whitespaces if any
    }
    return lastReadByte
  }

  private fun ImageInputStream.skipUntil(vararg expected: Char) {
    while (!readNextEquals(expected)) {
      // skip until expected chars or EOF
    }
  }

  private fun ImageInputStream.readNextEquals(expected: CharArray): Boolean {
    // first char is read with readByte()
    require(expected.isNotEmpty())
    val first = (readByte() and 0xFF.toByte()).toInt().toChar()
    if (first != expected[0]) return false

    // next can be read with read()
    expected.forEachIndexed { index, it ->
      if (index == 0) return@forEachIndexed
      val read = read()
      if (read != it.code) return false
    }
    return true
  }

  private fun ByteArray.startsWith(vararg expected: Char): Boolean {
    expected.forEachIndexed { index, c ->
      if (this[index] != c.code.toByte()) return false
    }
    return true
  }

  override fun createReaderInstance(extension: Any?): ImageReader {
    return SvgImageReader(this)
  }
}

