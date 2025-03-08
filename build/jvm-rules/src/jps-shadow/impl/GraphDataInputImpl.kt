package org.jetbrains.jps.dependency.impl

import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import java.io.DataInput
import java.util.function.Function

open class GraphDataInputImpl(
  private val delegate: DataInput,
  private val stringEnumerator: StringEnumerator?,
) : GraphDataInput {
  companion object {
    @Suppress("unused")
    @JvmStatic
    fun wrap(`in`: DataInput): GraphDataInput {
      throw UnsupportedOperationException("wrap) must not be used")
    }

    @Suppress("unused")
    @JvmStatic
    fun wrap(`in`: DataInput, enumerator: StringEnumerator?, elementInterner: Function<Any, Any>?): GraphDataInput {
      throw UnsupportedOperationException("wrap) must not be used")
    }
  }

  override fun readFully(b: ByteArray) {
    delegate.readFully(b)
  }

  override fun readFully(b: ByteArray, off: Int, len: Int) {
    delegate.readFully(b, off, len)
  }

  override fun skipBytes(n: Int): Int {
    return delegate.skipBytes(n)
  }

  override fun readBoolean(): Boolean {
    return delegate.readBoolean()
  }

  override fun readByte(): Byte {
    return delegate.readByte()
  }

  override fun readUnsignedByte(): Int {
    return delegate.readUnsignedByte()
  }

  override fun readShort(): Short {
    return delegate.readShort()
  }

  override fun readUnsignedShort(): Int {
    return delegate.readUnsignedShort()
  }

  override fun readChar(): Char {
    return delegate.readChar()
  }

  override fun readInt(): Int {
    return DataInputOutputUtil.readINT(delegate)
  }

  override fun readLong(): Long {
    return DataInputOutputUtil.readLONG(delegate)
  }

  override fun readRawLong(): Long {
    return delegate.readLong()
  }

  override fun readFloat(): Float {
    return delegate.readFloat()
  }

  override fun readDouble(): Double {
    return delegate.readDouble()
  }

  override fun readLine(): String? {
    return delegate.readLine()
  }

  override fun readUTF(): String {
    if (stringEnumerator == null) {
      return IOUtil.readUTF(delegate)
    }
    else {
      val id = delegate.readInt()
      return stringEnumerator.valueOf(id)
    }
  }

  override fun <T : ExternalizableGraphElement> readGraphElement(): T {
    return doReadGraphElement(this) {
      processLoadedGraphElement(it)
    }
  }

  override fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C {
    return doReadGraphElementCollection(this, result) {
      processLoadedGraphElement(it)
    }
  }

  protected open fun <T : ExternalizableGraphElement> processLoadedGraphElement(element: T): T = element
}

internal inline fun <T : ExternalizableGraphElement> doReadGraphElement(
  input: GraphDataInput,
  processLoadedGraphElement: (element: T) -> T,
): T {
  val classInfo = ClassRegistry.read(input)
  val constructor = if (classInfo.isFactored) {
    val factorData = input.readGraphElement<ExternalizableGraphElement>()
    classInfo.constructor.bindTo(factorData)
  }
  else {
    classInfo.constructor
  }
  @Suppress("UNCHECKED_CAST")
  return processLoadedGraphElement(constructor.invoke(input) as T)
}

internal inline fun <C : MutableCollection<in T>, T : ExternalizableGraphElement> doReadGraphElementCollection(
  input: GraphDataInput,
  result: C,
  processLoadedGraphElement: (element: T) -> T,
): C {
  val classInfo = ClassRegistry.read(input)
  val constructor = classInfo.constructor
  if (classInfo.isFactored) {
    repeat(input.readInt()) {
      val factorData = input.readGraphElement<ExternalizableGraphElement>()
      val constructor = constructor.bindTo(factorData)
      repeat(input.readInt()) {
        @Suppress("UNCHECKED_CAST")
        result.add(processLoadedGraphElement(constructor.invoke(input) as T))
      }
    }
  }
  else {
    repeat(input.readInt()) {
      @Suppress("UNCHECKED_CAST")
      result.add(processLoadedGraphElement(constructor.invoke(input) as T))
    }
  }
  return result
}