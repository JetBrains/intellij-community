// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.windowsProcessApi

import com.intellij.execution.Platform
import java.io.IOException
import java.io.InputStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.util.Objects

/**
 * Reads the parent end of an anonymous pipe created for a [WindowsProcess].
 *
 * [close] closes the handle, so the child meets a broken pipe on the next write.
 */
internal class WindowsInputStream(private val readHandle: MemorySegment) : InputStream() {
  // Any thread can read the stream, and the segments live as long as this object, so the garbage collector frees them.
  private val arena = Arena.ofAuto()
  private val callState = arena.allocate(Kernel32.CALL_STATE)
  private val count = arena.allocate(JAVA_INT)
  // `ReadFile` writes into native memory, so it cannot fill the caller array. Read here and copy.
  private val buffer = arena.allocate(BUFFER_SIZE.toLong())
  private var closed = false

  init {
    require(Platform.current() == Platform.WINDOWS) { "WindowsInputStream works on Windows only" }
  }

  override fun read(): Int {
    val oneByte = ByteArray(1)
    return when (read(oneByte, 0, 1)) {
      -1 -> -1
      else -> oneByte[0].toInt() and 0xff
    }
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    Objects.checkFromIndexSize(off, len, b.size)
    if (closed) throw IOException("Stream is closed")
    if (len == 0) return 0

    val bytesToRead = minOf(BUFFER_SIZE, len)
    if (!Kernel32.readFile(callState, readHandle, buffer, bytesToRead, count)) {
      val lastError = Kernel32.lastError(callState)
      // A closed write end is the normal end of the stream, not a failure.
      // https://learn.microsoft.com/en-us/windows/win32/api/namedpipeapi/nf-namedpipeapi-createpipe
      if (lastError == Kernel32.ERROR_BROKEN_PIPE) return -1
      throw IOException(Kernel32.formatWin32Error("ReadFile", lastError))
    }

    val bytesRead = count.get(JAVA_INT, 0)
    if (bytesRead <= 0) return -1
    MemorySegment.copy(buffer, JAVA_BYTE, 0, b, off, bytesRead)
    return bytesRead
  }

  override fun available(): Int {
    if (closed) throw IOException("Stream is closed")
    if (!Kernel32.peekNamedPipe(callState, readHandle, count)) {
      val lastError = Kernel32.lastError(callState)
      if (lastError == Kernel32.ERROR_BROKEN_PIPE) return 0
      throw IOException(Kernel32.formatWin32Error("PeekNamedPipe", lastError))
    }
    return count.get(JAVA_INT, 0)
  }

  override fun close() {
    if (closed) return
    closed = true
    if (!Kernel32.closeHandle(callState, readHandle)) {
      throw IOException(Kernel32.formatWin32Error("CloseHandle", Kernel32.lastError(callState)))
    }
  }

  private companion object {
    const val BUFFER_SIZE = 4096
  }
}
