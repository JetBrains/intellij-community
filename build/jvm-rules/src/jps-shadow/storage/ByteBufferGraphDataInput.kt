// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.impl.doReadGraphElement
import org.jetbrains.jps.dependency.impl.doReadGraphElementCollection
import java.nio.ByteBuffer

class ByteBufferGraphDataInput(
  private val buffer: ByteBuffer,
  private val elementInterner: ((ExternalizableGraphElement) -> ExternalizableGraphElement)?,
) : GraphDataInput {
  @Suppress("UNCHECKED_CAST")
  private val strings = predefinedStrings.clone() as ArrayList<String>

  override fun <T : ExternalizableGraphElement> readGraphElement(): T {
    return doReadGraphElement(this) {
      @Suppress("UNCHECKED_CAST")
      if (elementInterner == null) it else elementInterner(it) as T
    }
  }

  override fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C {
    return doReadGraphElementCollection(this, result) {
      @Suppress("UNCHECKED_CAST")
      if (elementInterner == null) it else elementInterner(it) as T
    }
  }

  override fun readRawLong(): Long = buffer.getLong()

  override fun readFully(b: ByteArray) {
    buffer.get(b)
  }

  override fun readFully(b: ByteArray, off: Int, len: Int) {
    buffer.get(b, off, len)
  }

  override fun skipBytes(n: Int): Int {
    buffer.position(buffer.position() + n)
    return n
  }

  override fun readBoolean(): Boolean = buffer.get() == 1.toByte()

  override fun readByte(): Byte = buffer.get()

  override fun readUnsignedByte(): Int = buffer.get().toInt() and 0xFF

  override fun readShort(): Short = buffer.getShort()

  override fun readUnsignedShort(): Int = buffer.getShort().toInt() and 0xFFFF

  override fun readChar(): Char = buffer.getChar()

  override fun readInt(): Int = buffer.getInt()

  override fun readLong(): Long = buffer.getLong()

  override fun readFloat(): Float = buffer.getFloat()

  override fun readDouble(): Double = buffer.getDouble()

  @Suppress("DuplicatedCode")
  override fun readUTF(): String {
    val lengthOrIndex = readUInt29(buffer)
    if ((lengthOrIndex and 1) == 1) {
      val length = lengthOrIndex shr 1
      val bytes = ByteArray(length)
      buffer.get(bytes)
      val string = String(bytes)
      strings.add(string)
      return string
    }
    else {
      return strings.get(lengthOrIndex shr 1)
    }
  }
}

@Suppress("DuplicatedCode")
private fun readUInt29(buffer: ByteBuffer): Int {
  var result = buffer.get().toInt()
  if ((result and 0x80) == 0) {
    return result
  }

  result = (result and 0x7F) shl 7
  var b = buffer.get().toInt()
  if ((b and 0x80) == 0) {
    return result or b
  }

  result = (result or (b and 0x7F)) shl 7
  b = buffer.get().toInt()
  if ((b and 0x80) == 0) {
    return result or b
  }

  result = (result or (b and 0x7F)) shl 8
  b = buffer.get().toInt()
  return result or (b and 0xFF)
}