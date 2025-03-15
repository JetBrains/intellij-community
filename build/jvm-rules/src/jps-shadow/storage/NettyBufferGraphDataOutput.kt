// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import androidx.collection.MutableObjectIntMap
import io.netty.buffer.ByteBuf
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataOutput

class NettyBufferGraphDataOutput(private val buffer: ByteBuf) : GraphDataOutput {
  private val strings = MutableObjectIntMap<String>(predefinedStringArray.size * 2).also {
    for ((i, s) in predefinedStringArray.withIndex()) {
      it.set(s, i)
    }
  }

  override fun <T : ExternalizableGraphElement> writeGraphElement(element: T) {
    doWriteGraphElement(this, element)
  }

  override fun <T : ExternalizableGraphElement> writeGraphElementCollection(
    elementType: Class<out T>,
    collection: Iterable<T>
  ) {
    doWriteGraphElementCollection(this, elementType, collection)
  }

  override fun writeRawLong(v: Long) {
    buffer.writeLongLE(v)
  }

  override fun write(v: ByteArray) {
    buffer.writeBytes(v)
  }

  override fun write(v: ByteArray, offset: Int, lenght: Int) {
    buffer.writeBytes(v, offset, lenght)
  }

  override fun writeBoolean(v: Boolean) {
    buffer.writeBoolean(v)
  }

  override fun writeByte(v: Int) {
    buffer.writeByte(v)
  }

  override fun writeShort(v: Int) {
    buffer.writeShortLE(v)
  }

  override fun writeChar(v: Int) {
    buffer.writeChar(v)
  }

  override fun writeInt(v: Int) {
    buffer.writeIntLE(v)
  }

  override fun writeLong(v: Long) {
    buffer.writeLongLE(v)
  }

  override fun writeFloat(v: Float) {
    buffer.writeFloatLE(v)
  }

  override fun writeDouble(v: Double) {
    buffer.writeDoubleLE(v)
  }

  override fun writeUTF(s: String) {
    val index = strings.getOrDefault(s, -1)
    if (index >= 0) {
      writeUInt29(buffer, index shl 1)
    }
    else {
      val bytes = s.toByteArray()
      writeUInt29(buffer, (bytes.size shl 1) or 1)
      buffer.writeBytes(bytes)
      strings.set(s, strings.size)
    }
  }
}

private fun writeUInt29(buffer: ByteBuf, value: Int) {
  when {
    value < 0x80 -> {
      buffer.writeByte(value)
    }
    value < 0x4000 -> {
      buffer.writeByte((value shr 7) or 0x80)
      buffer.writeByte(value and 0x7F)
    }
    value < 0x200000 -> {
      buffer.writeByte((value shr 14) or 0x80)
      buffer.writeByte((value shr 7) or 0x80)
      buffer.writeByte(value and 0x7F)
    }
    else -> {
      buffer.writeByte((value shr 22) or 0x80)
      buffer.writeByte((value shr 15) or 0x80)
      buffer.writeByte((value shr 8) or 0x80)
      buffer.writeByte(value and 0xFF)
    }
  }
}
