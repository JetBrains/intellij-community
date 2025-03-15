@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import io.netty.buffer.ByteBuf
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.impl.doReadGraphElement
import org.jetbrains.jps.dependency.impl.doReadGraphElementCollection

internal val predefinedStringArray = arrayOf(
  "",
  "<init>",
  "Ljava/lang/String;",
  "Ljava/lang/CharSequence;",
  "Ljava/lang/Object;",
  "Ljava/awt/datatransfer/DataFlavor;",
  "Ljava/util/List;",
  "Lkotlin/coroutines/Continuation;",
  "Ljavax/swing/JComponent;",
  "Ljava/nio/ByteBuffer;",
  "Ljava/io/InputStream;",
  "java/io/IOException",
  "Ljava/net/URL;",
  "java/lang/Deprecated;",
  "org/jetbrains/annotations/Nullable",
  "org/jetbrains/annotations/NotNull",
  "V",
  "I",
  "Z",
  "[B",
  "<T:Ljava/lang/Object;>Ljava/lang/Object;"
)

private val predefinedStrings = ArrayList<String>(predefinedStringArray.asList())

class NettyBufferGraphDataInput(private val buffer: ByteBuf) : GraphDataInput {
  @Suppress("UNCHECKED_CAST")
  private val strings = predefinedStrings.clone() as ArrayList<String>

  override fun <T : ExternalizableGraphElement> readGraphElement(): T {
    return doReadGraphElement(this) { it }
  }

  override fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C {
    return doReadGraphElementCollection(this, result) { it }
  }

  override fun readRawLong(): Long = buffer.readLongLE()

  override fun readFully(b: ByteArray) {
    buffer.readBytes(b)
  }

  override fun readFully(b: ByteArray, off: Int, len: Int) {
    buffer.readBytes(b, off, len)
  }

  override fun skipBytes(n: Int): Int {
    buffer.skipBytes(n)
    return n
  }

  override fun readBoolean(): Boolean = buffer.readBoolean()

  override fun readByte(): Byte = buffer.readByte()

  override fun readUnsignedByte(): Int = buffer.readUnsignedByte().toInt()

  override fun readShort(): Short = buffer.readShortLE()

  override fun readUnsignedShort(): Int = buffer.readUnsignedShortLE()

  override fun readChar(): Char = buffer.readChar()

  override fun readInt(): Int = buffer.readIntLE()

  override fun readLong(): Long = buffer.readLongLE()

  override fun readFloat(): Float = buffer.readFloatLE()

  override fun readDouble(): Double = buffer.readDoubleLE()

  override fun readUTF(): String {
    val lengthOrIndex = readUInt29(buffer)
    if ((lengthOrIndex and 1) == 1) {
      val length = lengthOrIndex shr 1
      val bytes = ByteArray(length)
      buffer.readBytes(bytes)
      val string = String(bytes)
      strings.add(string)
      return string
    }
    else {
      return strings.get(lengthOrIndex shr 1)
    }
  }
}

private fun readUInt29(buffer: ByteBuf): Int {
  var result = buffer.readByte().toInt()
  if ((result and 0x80) == 0) {
    return result
  }

  result = (result and 0x7F) shl 7
  var b = buffer.readByte().toInt()
  if ((b and 0x80) == 0) {
    return result or b
  }

  result = (result or (b and 0x7F)) shl 7
  b = buffer.readByte().toInt()
  if ((b and 0x80) == 0) {
    return result or b
  }

  result = (result or (b and 0x7F)) shl 8
  b = buffer.readByte().toInt()
  return result or (b and 0xFF)
}