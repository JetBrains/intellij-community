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

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class FileBackedUShortList(private val buffer: ByteBuffer) : UShortList {

  override operator fun get(index: Int): Int {
    buffer.position(index * 2)
    return java.lang.Short.toUnsignedInt(buffer.short)
  }

  override operator fun set(index: Int, value: Int) {
    assert(value in 0..65535)
    buffer.position(index * 2)
    buffer.putShort(value.toShort())
  }

  companion object {
    fun createEmpty(channel: FileChannel, size: Long): UShortList {
      FileBackedHashMap.createEmptyFile(channel, size * 2)
      return FileBackedUShortList(channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size()))
    }
  }
}