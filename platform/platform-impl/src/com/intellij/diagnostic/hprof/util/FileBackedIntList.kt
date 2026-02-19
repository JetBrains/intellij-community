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

@ApiStatus.Internal
class FileBackedIntList(private val buffer: ByteBuffer) : IntList {

  override operator fun get(index: Int): Int {
    buffer.position(index * 4)
    return buffer.int
  }

  override operator fun set(index: Int, value: Int) {
    buffer.position(index * 4)
    buffer.putInt(value)
  }

  companion object {
    fun createEmpty(channel: FileChannel, size: Long): IntList {
      FileBackedHashMap.createEmptyFile(channel, size * 4)
      return FileBackedIntList(channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size()))
    }
  }
}