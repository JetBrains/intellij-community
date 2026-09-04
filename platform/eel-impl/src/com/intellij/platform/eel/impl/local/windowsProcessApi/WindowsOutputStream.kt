// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.windowsProcessApi

import com.intellij.execution.Platform
import java.io.IOException
import java.io.OutputStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.util.Objects

/**
 * Writes the parent end of an anonymous pipe created for a [WindowsProcess].
 *
 * [close] closes the handle, so the child reads the end of its standard input.
 */
internal class WindowsOutputStream(private val writeHandle: MemorySegment) : OutputStream() {
  // Any thread can write the stream, and the segments live as long as this object, so the garbage collector frees them.
  private val arena = Arena.ofAuto()
  private val callState = arena.allocate(Kernel32.CALL_STATE)
  private val count = arena.allocate(JAVA_INT)
  // `WriteFile` reads from native memory, so it cannot send the caller array. Copy here and send.
  private val buffer = arena.allocate(BUFFER_SIZE.toLong())
  private var closed = false

  init {
    require(Platform.current() == Platform.WINDOWS) { "WindowsOutputStream works on Windows only" }
  }

  override fun write(b: Int) {
    write(byteArrayOf(b.toByte()), 0, 1)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (closed) throw IOException("Stream is closed")

    var written = 0
    while (written < len) {
      val bytesToWrite = minOf(BUFFER_SIZE, len - written)
      MemorySegment.copy(b, off + written, buffer, JAVA_BYTE, 0, bytesToWrite)

      if (!Kernel32.writeFile(callState, writeHandle, buffer, bytesToWrite, count)) {
        throw IOException(Kernel32.formatWin32Error("WriteFile", Kernel32.lastError(callState)))
      }
      // A short write is legal, so advance by what the OS took, not by what was offered.
      val bytesWritten = count.get(JAVA_INT, 0)
      if (bytesWritten <= 0) {
        throw IOException("WriteFile accepted none of the $bytesToWrite bytes")
      }
      written += bytesWritten
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    if (!Kernel32.closeHandle(callState, writeHandle)) {
      throw IOException(Kernel32.formatWin32Error("CloseHandle", Kernel32.lastError(callState)))
    }
  }

  private companion object {
    const val BUFFER_SIZE = 4096
  }
}
