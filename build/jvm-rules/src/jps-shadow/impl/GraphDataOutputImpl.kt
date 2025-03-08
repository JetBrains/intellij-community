@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.impl

import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataOutput
import java.io.DataOutput
import kotlin.jvm.javaClass

open class GraphDataOutputImpl(
  private val delegate: DataOutput,
  private val stringEnumerator: StringEnumerator?,
) : GraphDataOutput {
  companion object {
    @JvmStatic
    @Suppress("unused")
    fun wrap(out: DataOutput): GraphDataOutput {
      throw UnsupportedOperationException("wrap) must not be used")
    }

    @Suppress("unused")
    @JvmStatic
    fun wrap(out: DataOutput, enumerator: StringEnumerator?): GraphDataOutput {
      throw UnsupportedOperationException("wrap) must not be used")
    }
  }

  override fun write(b: Int) {
    delegate.write(b)
  }

  override fun write(b: ByteArray) {
    delegate.write(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    delegate.write(b, off, len)
  }

  override fun writeBoolean(v: Boolean) {
    delegate.writeBoolean(v)
  }

  override fun writeByte(v: Int) {
    delegate.writeByte(v)
  }

  override fun writeShort(v: Int) {
    delegate.writeShort(v)
  }

  override fun writeChar(v: Int) {
    delegate.writeChar(v)
  }

  override fun writeInt(v: Int) {
    DataInputOutputUtil.writeINT(delegate, v)
  }

  override fun writeLong(v: Long) {
    DataInputOutputUtil.writeLONG(delegate, v)
  }

  override fun writeRawLong(v: Long) {
    delegate.writeLong(v)
  }

  override fun writeFloat(v: Float) {
    delegate.writeFloat(v)
  }

  override fun writeDouble(v: Double) {
    delegate.writeDouble(v)
  }

  override fun writeUTF(s: String) {
    if (stringEnumerator == null) {
      IOUtil.writeUTF(delegate, s)
    }
    else {
      delegate.writeInt(stringEnumerator.enumerate(s))
    }
  }

  override fun <T : ExternalizableGraphElement> writeGraphElement(element: T) {
    doWriteGraphElement(this, element)
  }

  override fun <T : ExternalizableGraphElement> writeGraphElementCollection(elementType: Class<out T>, collection: Iterable<T>) {
    doWriteGraphElementCollection(this, elementType, collection)
  }

  // Represent smaller integers with fewer bytes using the most significant bit of each byte. The worst case uses 32-bits
  // to represent a 29-bit number, which is what we would have done with no compression.
  protected fun writeUInt29(v: Int) {
    val out = delegate
    when {
      v < 0x80 -> out.write(v)
      v < 0x4000 -> {
        out.write((v shr 7 and 0x7F or 0x80))
        out.write(v and 0x7F)
      }
      v < 0x200000 -> {
        out.write((v shr 14 and 0x7F or 0x80))
        out.write(v shr 7 and 0x7F or 0x80)
        out.write(v and 0x7F)
      }
      v < 0x40000000 -> {
        out.write(v shr 22 and 0x7F or 0x80)
        out.write(v shr 15 and 0x7F or 0x80)
        out.write(v shr 8 and 0x7F or 0x80)
        out.write(v and 0xFF)
      }
      else -> throw IllegalArgumentException("Integer out of range: $v")
    }
  }
}

internal fun <T : ExternalizableGraphElement> doWriteGraphElement(output: GraphDataOutput, element: T) {
  ClassRegistry.writeClassId(element.javaClass, output)
  if (element is FactoredExternalizableGraphElement<*>) {
    output.writeGraphElement(element.getFactorData())
  }
  element.write(output)
}

internal fun <T : ExternalizableGraphElement> doWriteGraphElementCollection(
  output: GraphDataOutput,
  elementType: Class<out T>,
  collection: Iterable<T>,
) {
  val classInfo = ClassRegistry.writeClassId(elementType, output)
  if (classInfo.isFactored) {
    val elementGroups = Object2ObjectOpenHashMap<ExternalizableGraphElement, MutableList<FactoredExternalizableGraphElement<*>>>()
    for (e in collection) {
      e as FactoredExternalizableGraphElement<*>
      elementGroups.computeIfAbsent(e.getFactorData()) { ArrayList() }.add(e)
    }
    output.writeInt(elementGroups.size)
    for ((key, list) in elementGroups.object2ObjectEntrySet().fastIterator()) {
      output.writeGraphElement(key)
      output.writeInt(list.size)
      for (t in list) {
        t.write(output)
      }
    }
  }
  else {
    collection as Collection<*>
    output.writeInt(collection.size)
    for (t in collection) {
      t.write(output)
    }
  }
}