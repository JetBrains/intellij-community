package org.jetbrains.jps.dependency.storage

import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.impl.doReadGraphElement
import org.jetbrains.jps.dependency.impl.doReadGraphElementCollection
import java.nio.ByteBuffer

internal class WriteBufGraphDataOutput(
  private val buffer: WriteBuffer,
  private val stringEnumerator: StringEnumerator,
) : GraphDataOutput {
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
    buffer.putVarInt(v)
  }

  override fun writeLong(v: Long) {
    buffer.putVarLong(v)
  }

  override fun writeFloat(v: Float) {
    buffer.putFloat(v)
  }

  override fun writeDouble(v: Double) {
    buffer.putDouble(v)
  }

  override fun writeUTF(s: String) {
    buffer.putVarInt(stringEnumerator.enumerate(s))
  }
}

internal class WriteBufGraphDataInput(
  private val buffer: ByteBuffer,
  private val stringEnumerator: StringEnumerator,
  private val elementInterner: ((ExternalizableGraphElement) -> ExternalizableGraphElement)?,
) : GraphDataInput {
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

  override fun readInt(): Int = DataUtils.readVarInt(buffer)

  override fun readLong(): Long = DataUtils.readVarLong(buffer)

  override fun readFloat(): Float = buffer.getFloat()

  override fun readDouble(): Double = buffer.getDouble()

  override fun readUTF(): String {
    return stringEnumerator.valueOf(DataUtils.readVarInt(buffer))
  }
}
