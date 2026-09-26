/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.util.lang.ByteBufferCleaner
import org.jetbrains.annotations.ApiStatus
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.InvalidMarkException
import java.nio.channels.FileChannel
import kotlin.math.min

@ApiStatus.Internal
class HProfReadBufferSlidingWindow internal constructor(
  private val parser: HProfEventBasedParser,
  private val totalSize: Long,
  private val mapper: (start: Long) -> ByteBuffer,
  private val windowSize: Int = DEFAULT_WINDOW_SIZE,
  private val initialBuffer: ByteBuffer = mapper(0L)
) : AbstractHProfNavigatorReadBuffer(parser) {
  constructor(channel: FileChannel, parser: HProfEventBasedParser) : this(
    parser,
    channel.size(),
    { start ->
      channel.map(
        FileChannel.MapMode.READ_ONLY,
        start,
        min(DEFAULT_WINDOW_SIZE.toLong(), channel.size() - start)
      )
    }
  )

  private var currentWindow: ByteBuffer = initialBuffer
  private var offsetOfCurrentWindow = 0L
  private var mark = -1L

  override fun close() {
    if (currentWindow.isDirect) {
      ByteBufferCleaner.unmapBuffer(currentWindow)
    }
  }

  override fun isEof(): Boolean {
    return !hasRemaining()
  }

  override fun limit(): Long {
    return totalSize
  }

  override fun position(): Long {
    return offsetOfCurrentWindow + currentWindow.position()
  }

  override fun position(newPosition: Long) {
    if (newPosition >= offsetOfCurrentWindow && newPosition < offsetOfCurrentWindow + currentWindow.limit()) {
      currentWindow.position((newPosition - offsetOfCurrentWindow).toInt())
    }
    else if (newPosition > totalSize) {
      throw BufferUnderflowException()
    }
    else {
      remapBuffer(newPosition)
    }
  }

  override fun get(bytes: ByteArray) {
    if (bytes.isEmpty()) {
      return
    }

    if (bytes.size <= currentWindow.remaining()) {
      currentWindow.get(bytes)
      return
    }

    var destinationOffset = 0
    while (destinationOffset < bytes.size) {
      if (!hasRemaining()) {
        throw BufferUnderflowException()
      }

      if (currentWindow.remaining() == 0) {
        remapBuffer(position())
      }

      val bytesToFetch = min(bytes.size - destinationOffset, currentWindow.remaining())
      currentWindow.get(bytes, destinationOffset, bytesToFetch)
      destinationOffset += bytesToFetch
    }
  }

  override fun getByteBuffer(size: Long): HProfReadBufferSlidingWindow {
    require(size >= 0) { "Buffer size must be non-negative: $size" }
    var useSlice = false
    if (size < currentWindow.remaining()) {
      useSlice = true
    }
    else if (size < windowSize) {
      remapBuffer(position())
      useSlice = true
    }

    val subBufferPosition = position()
    val newMapper: (Long) -> ByteBuffer = { subBufferStart ->
      mapper(subBufferStart + subBufferPosition)
    }

    if (useSlice) {
      // size.toInt causes no overflow, as its upper bound is either buffer's capacity or windowSize, and either fits in Int
      val sizeInt = size.toInt()
      val slicedBuffer = currentWindow.slice()
      slicedBuffer.limit(sizeInt)
      skip(size)
      return HProfReadBufferSlidingWindow(
        parser,
        size,
        newMapper,
        windowSize,
        initialBuffer = slicedBuffer.asReadOnlyBuffer(),
      )
    }
    else if (size < Int.MAX_VALUE) {
      val sizeInt = size.toInt()
      val bytes = ByteArray(sizeInt)
      get(bytes)
      return HProfReadBufferSlidingWindow(parser, size, newMapper, windowSize, initialBuffer = ByteBuffer.wrap(bytes))
    }
    else {
      val currentGlobalPosition = position()
      val initialBuffer = mapper(currentGlobalPosition)
      position(currentGlobalPosition + size)
      return HProfReadBufferSlidingWindow(parser, size, newMapper, windowSize, initialBuffer = initialBuffer)
    }
  }

  override fun get(): Byte {
    ensureRemaining(Byte.SIZE_BYTES)
    return currentWindow.get()
  }

  override fun getShort(): Short {
    ensureRemaining(Short.SIZE_BYTES)
    return currentWindow.short
  }

  override fun getInt(): Int {
    ensureRemaining(Int.SIZE_BYTES)
    return currentWindow.int
  }

  override fun getLong(): Long {
    ensureRemaining(Long.SIZE_BYTES)
    return currentWindow.long
  }

  fun remaining(): Long = totalSize - position()

  fun hasRemaining(): Boolean = remaining() > 0

  fun mark() {
    mark = position()
  }

  fun reset() {
    if (mark < 0) {
      throw InvalidMarkException()
    }
    position(mark)
  }

  fun getChar(): Char {
    ensureRemaining(Char.SIZE_BYTES)
    return currentWindow.char
  }

  fun getFloat(): Float {
    ensureRemaining(Float.SIZE_BYTES)
    return currentWindow.float
  }

  fun getDouble(): Double {
    ensureRemaining(Double.SIZE_BYTES)
    return currentWindow.double
  }

  fun get(index: Int): Byte = readAt(index.toLong()) { get() }

  fun getShort(index: Int): Short = readAt(index.toLong()) { getShort() }

  fun getChar(index: Int): Char = readAt(index.toLong()) { getChar() }

  fun getInt(index: Int): Int = readAt(index.toLong()) { getInt() }

  fun getFloat(index: Int): Float = readAt(index.toLong()) { getFloat() }

  fun getDouble(index: Int): Double = readAt(index.toLong()) { getDouble() }

  fun getLong(index: Int): Long = readAt(index.toLong()) { getLong() }

  internal fun currentWindow(): ByteBuffer {
    if (!hasRemaining()) {
      return EMPTY_BUFFER
    }
    if (!currentWindow.hasRemaining()) {
      remapBuffer(position())
    }
    return currentWindow.slice().asReadOnlyBuffer()
  }

  private fun ensureRemaining(byteCount: Int) {
    if (remaining() < byteCount) {
      throw BufferUnderflowException()
    }

    if (currentWindow.remaining() < byteCount) {
      remapBuffer(position())
    }
  }

  private inline fun <T> readAt(index: Long, reader: HProfReadBufferSlidingWindow.() -> T): T {
    val oldPosition = position()
    position(index)
    return try {
      reader()
    }
    finally {
      position(oldPosition)
    }
  }

  private fun remapBuffer(newPosition: Long) {
    val oldWindow = currentWindow
    val bytesToMap = min(windowSize.toLong(), totalSize - newPosition)
    offsetOfCurrentWindow = newPosition
    currentWindow = if (bytesToMap == 0L) EMPTY_BUFFER else mapper(offsetOfCurrentWindow)

    if (oldWindow.isDirect) {
      ByteBufferCleaner.unmapBuffer(oldWindow)
    }
  }

  companion object {
    private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()
    private const val DEFAULT_WINDOW_SIZE = 10_000_000
  }
}
