// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore.kotlin

import com.intellij.util.io.DataExternalizer
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.BasicDataType
import java.io.DataInput
import java.io.DataOutput
import java.nio.ByteBuffer

private val emptyArray = arrayOfNulls<Any>(0)

internal fun <V : Any> createDataTypeAdapter(valueExternalizer: DataExternalizer<V>): BasicDataType<V> = DataTypeAdapter(valueExternalizer)

private class DataTypeAdapter<V : Any>(private val valueExternalizer: DataExternalizer<V>) : BasicDataType<V>() {
  override fun getMemory(obj: V?): Int = 0

  override fun write(buffer: WriteBuffer, obj: V?) {
    valueExternalizer.save(WriteBufferDataOutput(buffer), obj)
  }

  override fun read(buff: ByteBuffer): V? {
    return valueExternalizer.read(ByteBufferDataInput(buff))
  }

  override fun createStorage(size: Int): Array<V?> {
    @Suppress("UNCHECKED_CAST")
    return (if (size == 0) emptyArray else arrayOfNulls(size)) as Array<V?>
  }

  override fun compare(a: V, b: V): Int {
    @Suppress("UNCHECKED_CAST")
    return (a as Comparable<V>).compareTo(b)
  }
}

internal class WriteBufferDataOutput(private val buffer: WriteBuffer) : DataOutput {
  override fun writeBytes(s: String) = throw UnsupportedOperationException("do not use")

  override fun writeChars(s: String) = throw UnsupportedOperationException("do not use")

  override fun write(v: Int) {
    writeInt(v)
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

  override fun writeUTF(s: String) {
    val bytes = s.toByteArray()
    buffer.putInt(bytes.size)
    buffer.put(bytes)
  }
}

internal class ByteBufferDataInput(
  private val buffer: ByteBuffer,
) : DataInput {
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

  override fun readLine(): String = throw UnsupportedOperationException()

  @Suppress("DuplicatedCode")
  override fun readUTF(): String {
    val bytes = ByteArray(buffer.getInt())
    buffer.get(bytes)
    return String(bytes)
  }
}