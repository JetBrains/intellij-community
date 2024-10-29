// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio.svg

import java.io.IOException
import java.io.InputStream
import javax.imageio.stream.ImageInputStream
import kotlin.math.max

internal class ImageInputStreamAdapter(private val imageInputStream: ImageInputStream) : InputStream() {
  private var closed = false

  @Throws(IOException::class)
  override fun close() {
    closed = true
  }

  @Throws(IOException::class)
  override fun read(): Int {
    if (closed) throw IOException("stream closed")
    return imageInputStream.read()
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (closed) throw IOException("stream closed")
    if (len <= 0) {
      return 0
    }
    return imageInputStream.read(b, off, max(len.toLong(), 0).toInt())
  }

  @Throws(IOException::class)
  override fun skip(n: Long): Long {
    if (closed) throw IOException("stream closed")
    if (n <= 0) {
      return 0
    }
    return imageInputStream.skipBytes(n)
  }

  override fun markSupported(): Boolean {
    return true
  }

  override fun mark(readlimit: Int) {
    if (closed) throw IOException("stream closed")
    imageInputStream.mark()
  }

  override fun reset() {
    if (closed) throw IOException("stream closed")
    imageInputStream.reset()
  }
}