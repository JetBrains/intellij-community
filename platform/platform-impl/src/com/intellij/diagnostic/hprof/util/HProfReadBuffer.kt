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

import java.nio.ByteBuffer
import java.security.InvalidParameterException

abstract class HProfReadBuffer : AutoCloseable {
  var idSize: Int = 0
    set(value) {
      if (idSize != 0)
        throw IllegalStateException("ID size cannot be reassigned.")
      if (value != 1 && value != 2 && value != 4 && value != 8)
        throw InvalidParameterException("ID size can only be 1, 2, 4 or 8.")
      field = value
    }

  abstract fun position(newPosition: Long)
  abstract fun isEof(): Boolean
  abstract fun position(): Long
  abstract fun get(bytes: ByteArray)
  abstract fun getByteBuffer(size: Int): ByteBuffer
  abstract fun get(): Byte
  abstract fun getShort(): Short
  abstract fun getInt(): Int
  abstract fun getLong(): Long

  open fun getId(): Long {
    return getRawId()
  }

  fun getRawId(): Long {
    return when (idSize) {
      1 -> getUnsignedByte().toLong()
      2 -> getUnsignedShort().toLong()
      4 -> getUnsignedInt()
      8 -> getLong()
      else -> throw IllegalArgumentException("ID size not assigned yet.")
    }
  }

  fun getUnsignedByte(): Int = java.lang.Byte.toUnsignedInt(get())
  fun getUnsignedShort(): Int = java.lang.Short.toUnsignedInt(getShort())
  fun getUnsignedInt(): Long = java.lang.Integer.toUnsignedLong(getInt())

  fun getUTF8String(): String {
    val length = getUnsignedShort()
    val bytes = ByteArray(length)
    get(bytes)
    return String(bytes)
  }

  fun skip(n: Int): Unit = position(position() + n)
}