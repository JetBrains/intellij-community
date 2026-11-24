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

import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class FileBackedHashMap private constructor(
  private val buffers: Array<ByteBuffer>,
  private val bucketCount: Int,
  private val keySize: Int,
  private val valueSize: Int,
  private val bucketsPerBufferShift: Int
) {
  private val bucketSize = keySize + valueSize
  private val bucketsPerBufferMask = (1 shl bucketsPerBufferShift) - 1
  private var filledBuckets = 0

  companion object {

    private const val FILL_RATIO = 1.33

    // One buffer holds 2^26 buckets, so a bucket never crosses a buffer boundary.
    private const val BUCKETS_PER_BUFFER_SHIFT = 26

    private fun getBucketCount(size: Long): Long = (size * FILL_RATIO).toLong()

    fun isSupported(size: Long): Boolean {
      return getBucketCount(size) <= Int.MAX_VALUE
    }

    fun getFileSize(size: Long, keySize: Int, valueSize: Int): Long {
      return getBucketCount(size) * (keySize + valueSize)
    }

    fun createEmpty(channel: FileChannel, size: Long, keySize: Int, valueSize: Int): FileBackedHashMap {
      return createEmpty(channel, size, keySize, valueSize, BUCKETS_PER_BUFFER_SHIFT)
    }

    /**
     * Creates the map with 2^[bucketsPerBufferShift] buckets in each buffer.
     * Tests use a small shift to put the buffer boundaries into a small file.
     */
    internal fun createEmpty(channel: FileChannel, size: Long, keySize: Int, valueSize: Int, bucketsPerBufferShift: Int): FileBackedHashMap {
      if (keySize != 4 && keySize != 8) {
        throw IllegalArgumentException("keySize must be 4 or 8.")
      }
      if (valueSize < 0) {
        throw IllegalArgumentException("valueSize must be positive.")
      }
      if (!isSupported(size)) {
        throw IllegalArgumentException("Size too large")
      }
      val bucketCount = getBucketCount(size).toInt()
      createEmptyFile(channel, getFileSize(size, keySize, valueSize))
      val bufferSize = (1L shl bucketsPerBufferShift) * (keySize + valueSize)
      val buffers = mapBuffers(channel, FileChannel.MapMode.READ_WRITE, bufferSize, bufferSize)
      return FileBackedHashMap(buffers, bucketCount, keySize, valueSize, bucketsPerBufferShift)
    }

    /**
     * Maps the file into buffers of at most [bufferSize] bytes that start every [bufferStep] bytes.
     * A [bufferSize] above [bufferStep] makes the neighbor buffers overlap.
     * A file of up to 2 GB gets one map, and the buffers are slices of it.
     */
    fun mapBuffers(channel: FileChannel, mode: FileChannel.MapMode, bufferStep: Long, bufferSize: Long): Array<ByteBuffer> {
      return mapBuffers(channel, mode, bufferStep, bufferSize, Int.MAX_VALUE.toLong())
    }

    /**
     * Maps a file of up to [singleMapLimit] bytes once.
     * Tests use a small limit to map each buffer of a small file on its own.
     */
    internal fun mapBuffers(channel: FileChannel, mode: FileChannel.MapMode, bufferStep: Long, bufferSize: Long,
                            singleMapLimit: Long): Array<ByteBuffer> {
      val fileSize = channel.size()
      val bufferCount = max(1L, (fileSize + bufferStep - 1) / bufferStep).toInt()
      val fileBuffer = if (fileSize <= singleMapLimit) channel.map(mode, 0, fileSize) else null
      return Array(bufferCount) { i ->
        val start = i * bufferStep
        val size = min(bufferSize, fileSize - start)
        if (fileBuffer != null) fileBuffer.slice(start.toInt(), size.toInt()) else channel.map(mode, start, size)
      }
    }

    fun createEmptyFile(channel: FileChannel, size: Long) {
      val bufferSize = 60_000
      val emptyBuf = ByteBuffer.allocateDirect(bufferSize)
      var remaining = size
      while (remaining > 0) {
        val toWrite = min(emptyBuf.remaining().toLong(), remaining)
        if (toWrite == emptyBuf.remaining().toLong()) {
          channel.write(emptyBuf)
          emptyBuf.rewind()
        }
        else {
          assert(toWrite <= bufferSize)
          emptyBuf.limit(toWrite.toInt())
          channel.write(emptyBuf)
        }
        remaining -= toWrite
      }
      channel.position(0)
    }
  }

  operator fun get(key: Long): ByteBuffer? {
    if (key == 0L)
      return null
    var bucketIndex = getBucketIndex(key)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val buffer = bucketBuffer(bucketIndex)
      val inspectedKey = readKey(buffer)
      if (inspectedKey == key)
        return buffer
      if (inspectedKey == 0L)
        return null
      bucketIndex = nextBucketIndex(bucketIndex)
      inspectedBuckets++
    }
    // Map is full
    return null
  }

  // Returns the buffer of the bucket at the position of the bucket key.
  private fun bucketBuffer(bucketIndex: Int): ByteBuffer {
    val buffer = buffers[bucketIndex ushr bucketsPerBufferShift]
    buffer.position((bucketIndex and bucketsPerBufferMask) * bucketSize)
    return buffer
  }

  private fun nextBucketIndex(bucketIndex: Int): Int = if (bucketIndex == bucketCount - 1) 0 else bucketIndex + 1

  private fun readKey(buffer: ByteBuffer) = if (keySize == 8) buffer.long else buffer.int.toLong()

  private fun getBucketIndex(key: Long): Int {
    return (key.hashCode() and Int.MAX_VALUE).rem(bucketCount)
  }

  fun put(key: Long): ByteBuffer {
    var bucketIndex = getBucketIndex(key)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val buffer = bucketBuffer(bucketIndex)
      val inspectedKey = readKey(buffer)
      if (inspectedKey == key || inspectedKey == 0L) {
        if (keySize == 4) {
          buffer.putInt(buffer.position() - keySize, key.toInt())
        }
        else {
          buffer.putLong(buffer.position() - keySize, key)
        }
        if (inspectedKey == 0L) {
          filledBuckets++
        }
        return buffer
      }
      bucketIndex = nextBucketIndex(bucketIndex)
      inspectedBuckets++
    }
    throw RuntimeException("HashMap is full.")
  }

  fun containsKey(key: Long): Boolean {
    if (key == 0L) return true
    return get(key) != null
  }

}