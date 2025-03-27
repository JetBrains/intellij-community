// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import org.jetbrains.jps.dependency.GraphDataOutput
import java.nio.ByteBuffer

class ByteBufferGraphDataOutput(private val buffer: ByteBuffer) : GraphDataOutput {
  private val stringMap = createStringMap()

  override fun writeRawLong(v: Long) {
    buffer.putLong(v)
  }

  override fun write(v: ByteArray) {
    buffer.put(v)
  }

  override fun write(v: ByteArray, offset: Int, lenght: Int) {
    buffer.put(v, offset, lenght)
  }

  override fun writeBoolean(v: Boolean) {
    buffer.put(if (v) 1 else 0)
  }

  override fun writeByte(v: Int) {
    buffer.put(v.toByte())
  }

  override fun writeShort(v: Int) {
    buffer.putShort(v.toShort())
  }

  override fun writeChar(v: Int) {
    buffer.putChar(v.toChar())
  }

  override fun writeInt(v: Int) {
    buffer.putInt(v)
  }

  override fun writeLong(v: Long) {
    buffer.putLong(v)
  }

  override fun writeFloat(v: Float) {
    buffer.putFloat(v)
  }

  override fun writeDouble(v: Double) {
    buffer.putDouble(v)
  }

  @Suppress("DuplicatedCode")
  override fun writeUTF(s: String) {
    val index = stringMap.getOrDefault(s, -1)
    if (index >= 0) {
      writeUInt29(buffer, index shl 1)
    }
    else {
      val bytes = s.toByteArray()
      writeUInt29(buffer, (bytes.size shl 1) or 1)
      buffer.put(bytes)
      stringMap.set(s, stringMap.size)
    }
  }
}

@Suppress("DuplicatedCode")
private fun writeUInt29(buffer: ByteBuffer, value: Int) {
  when {
    value < 0x80 -> {
      buffer.put(value.toByte())
    }

    value < 0x4000 -> {
      buffer.put(((value shr 7) or 0x80).toByte())
      buffer.put((value and 0x7F).toByte())
    }

    value < 0x200000 -> {
      buffer.put(((value shr 14) or 0x80).toByte())
      buffer.put(((value shr 7) or 0x80).toByte())
      buffer.put((value and 0x7F).toByte())
    }

    else -> {
      buffer.put(((value shr 22) or 0x80).toByte())
      buffer.put(((value shr 15) or 0x80).toByte())
      buffer.put(((value shr 8) or 0x80).toByte())
      buffer.put((value and 0xFF).toByte())
    }
  }
}