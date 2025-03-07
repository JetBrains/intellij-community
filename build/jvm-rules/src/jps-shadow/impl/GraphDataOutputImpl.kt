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

class GraphDataOutputImpl(
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

  override fun writeBytes(s: String) {
    delegate.writeBytes(s)
  }

  override fun writeChars(s: String) {
    delegate.writeChars(s)
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
    ClassRegistry.writeClassId(element.javaClass, this)
    if (element is FactoredExternalizableGraphElement<*>) {
      writeGraphElement(element.getFactorData())
    }
    element.write(this)
  }

  override fun <T : ExternalizableGraphElement> writeGraphElementCollection(elementType: Class<out T>, collection: Iterable<T>) {
    val classInfo = ClassRegistry.writeClassId(elementType, this)
    if (classInfo.isFactored) {
      val elementGroups = Object2ObjectOpenHashMap<ExternalizableGraphElement, MutableList<FactoredExternalizableGraphElement<*>>>()
      for (e in collection) {
        e as FactoredExternalizableGraphElement<*>
        elementGroups.computeIfAbsent(e.getFactorData()) { ArrayList() }.add(e)
      }
      writeInt(elementGroups.size)
      for ((key, list) in elementGroups.object2ObjectEntrySet().fastIterator()) {
        writeGraphElement(key)
        writeInt(list.size)
        for (t in list) {
          t.write(this)
        }
      }
    }
    else {
      collection as Collection<*>
      writeInt(collection.size)
      for (t in collection) {
        t.write(this)
      }
    }
  }
}