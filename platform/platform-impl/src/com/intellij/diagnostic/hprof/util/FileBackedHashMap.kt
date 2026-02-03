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
import kotlin.math.min

@ApiStatus.Internal
class FileBackedHashMap(
  private val buffer: ByteBuffer,
  private val keySize: Int,
  private val valueSize: Int
) {
  private val bucketCount: Int
  private val bucketSize = keySize + valueSize
  private var filledBuckets = 0

  init {
    val fileSize = buffer.remaining()
    assert(fileSize % bucketSize == 0)
    bucketCount = fileSize / bucketSize
  }

  companion object {

    private const val FILL_RATIO = 1.33

    private fun getFileSize(size: Long, keySize: Int, valueSize: Int): Long {
      return (size * FILL_RATIO).toLong() * (keySize + valueSize)
    }

    fun isSupported(size: Long, keySize: Int, valueSize: Int): Boolean {
      return getFileSize(size, keySize, valueSize) < Int.MAX_VALUE
    }

    fun createEmpty(channel: FileChannel, size: Long, keySize: Int, valueSize: Int): FileBackedHashMap {
      if (keySize != 4 && keySize != 8) {
        throw IllegalArgumentException("keySize must be 4 or 8.")
      }
      if (valueSize < 0) {
        throw IllegalArgumentException("valueSize must be positive.")
      }
      if (!isSupported(size, keySize, valueSize)) {
        throw IllegalArgumentException("Size too large")
      }
      val fileSize = getFileSize(size, keySize, valueSize)
      assert(fileSize <= Int.MAX_VALUE)
      createEmptyFile(channel, fileSize)
      val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size())
      return FileBackedHashMap(buffer, keySize, valueSize)
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
    val hashcode = getBucketIndex(key)
    buffer.position(hashcode * bucketSize)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val inspectedKey = readKey()
      if (inspectedKey == key)
        return buffer
      if (inspectedKey == 0L)
        return null
      if (buffer.remaining() <= valueSize) {
        buffer.position(0)
      }
      else {
        buffer.position(buffer.position() + valueSize)
      }
      inspectedBuckets++
    }
    // Map is full
    return null
  }

  private fun readKey() = if (keySize == 8) buffer.long else buffer.int.toLong()

  private fun getBucketIndex(key: Long): Int {
    return (key.hashCode() and Int.MAX_VALUE).rem(bucketCount)
  }

  fun put(key: Long): ByteBuffer {
    buffer.position(getBucketIndex(key) * bucketSize)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val inspectedKey = readKey()
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
      if (buffer.remaining() <= valueSize) {
        buffer.position(0)
      }
      else {
        buffer.position(buffer.position() + valueSize)
      }
      inspectedBuckets++
    }
    throw RuntimeException("HashMap is full.")
  }

  fun containsKey(key: Long): Boolean {
    if (key == 0L) return true
    return get(key) != null
  }

}