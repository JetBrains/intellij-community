// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

@ApiStatus.Internal
class InputStreamSkippingBOM(stream: InputStream, private val file: VirtualFile) : InputStream() {
  @Volatile
  private var readBom = true
  private val readBomLock = Any()
  private val stream: InputStream = if (!stream.markSupported()) BufferedInputStream(stream) else stream

  private fun readBomIfNeeded() {
    if (readBom) {
      synchronized(readBomLock) {
        if (!readBom) return
        val bom = CharsetToolkit.detectBOMFromStream(stream)
        if (bom != null && file.bom == null) {
          // this method was called before com.intellij.openapi.fileEditor.impl.LoadTextUtil.detectCharsetAndSetBOM
          file.bom = bom
        }
        readBom = false
      }
    }
  }

  @get:TestOnly
  val sourceStream: InputStream get() = stream

  @Throws(IOException::class)
  override fun read(): Int {
    readBomIfNeeded()
    return stream.read()
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray): Int {
    readBomIfNeeded()
    return stream.read(b)
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray, off: Int, len: Int): Int {
    readBomIfNeeded()
    return stream.read(b, off, len)
  }

  @Throws(IOException::class)
  override fun skip(n: Long): Long {
    readBomIfNeeded()
    return stream.skip(n)
  }

  @Throws(IOException::class)
  override fun available(): Int {
    readBomIfNeeded()
    return stream.available()
  }

  @Throws(IOException::class)
  override fun close() {
    stream.close()
  }

  override fun reset() {
    stream.reset()
  }

  override fun mark(readlimit: Int) {
    readBomIfNeeded()
    stream.mark(readlimit)
  }

  override fun markSupported(): Boolean {
    return true
  }

}
