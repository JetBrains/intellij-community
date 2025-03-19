/*
 * Copyright (C) 2019 The Android Open Source Project
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

@ApiStatus.Internal
class FileBackedUByteList(private val buffer: ByteBuffer) : UByteList {

  override operator fun get(index: Int): Int {
    return java.lang.Byte.toUnsignedInt(buffer.get(index))
  }

  override operator fun set(index: Int, value: Int) {
    assert(value in 0..255)
    buffer.put(index, value.toByte())
  }

  companion object {
    fun createEmpty(channel: FileChannel, size: Long): UByteList {
      FileBackedHashMap.createEmptyFile(channel, size)
      return FileBackedUByteList(channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size()))
    }
  }
}