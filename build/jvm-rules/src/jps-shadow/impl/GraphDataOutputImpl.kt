@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.impl

import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
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

  final override fun <T : ExternalizableGraphElement> writeGraphElement(element: T) {
    writeUTF(element.javaClass.getName())
    if (element is FactoredExternalizableGraphElement<*>) {
      writeGraphElement(element.getFactorData())
    }
    element.write(this)
  }

  override fun <T : ExternalizableGraphElement> writeGraphElementCollection(elemType: Class<out T>, col: Iterable<T>) {
    writeUTF(elemType.getName())
    if (FactoredExternalizableGraphElement::class.java.isAssignableFrom(elemType)) {
      val elementGroups = Object2ObjectLinkedOpenHashMap<ExternalizableGraphElement, MutableList<FactoredExternalizableGraphElement<*>>>()
      for (e in col) {
        e as FactoredExternalizableGraphElement<*>
        elementGroups.computeIfAbsent(e.getFactorData()) { ArrayList() }.add(e)
      }
      writeInt(elementGroups.size)
      for ((key, list) in elementGroups.object2ObjectEntrySet().fastIterator()) {
        var commonPartWritten = false
        writeInt(list.size)
        for (t in list) {
          if (!commonPartWritten) {
            commonPartWritten = true
            writeGraphElement(key)
          }
          t.write(this)
        }
      }
    }
    else {
      col as Collection<*>
      writeInt(col.size)
      for (t in col) {
        t.write(this)
      }
    }
  }
}