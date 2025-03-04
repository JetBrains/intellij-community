package org.jetbrains.jps.dependency.impl

import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import java.io.DataInput
import java.io.IOException
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.Function

private val defaultReadConstructorType = MethodType.methodType(Void.TYPE, GraphDataInput::class.java)
private val lookup = MethodHandles.lookup()

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
    val constructor: MethodHandle
    val className = readUTF()
    val elementType = Class.forName(className)
    if (FactoredExternalizableGraphElement::class.java.isAssignableFrom(elementType)) {
      val factorData = readGraphElement<ExternalizableGraphElement>()
      constructor = lookup
        .findConstructor(elementType, MethodType.methodType(Void.TYPE, factorData.javaClass, GraphDataInput::class.java))
        .bindTo(factorData)
    }
    else {
      constructor = lookup.findConstructor(elementType, defaultReadConstructorType)
    }
    @Suppress("UNCHECKED_CAST")
    return processLoadedGraphElement(constructor.invoke(this) as T)
  }

  override fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C {
    val className = readUTF()
    val elementType = Class.forName(className)
    if (FactoredExternalizableGraphElement::class.java.isAssignableFrom(elementType)) {
      var subGroupCount = readInt()
      while (subGroupCount-- > 0) {
        // per subgroup - must null for each subgroup as we must call readGraphElement for the first element
        var constructor: MethodHandle? = null
        var size = readInt()
        // first element
        while (size-- > 0) {
          if (constructor == null) {
            // first element
            val factorData = readGraphElement<ExternalizableGraphElement>()
            constructor = lookup
              .findConstructor(elementType, MethodType.methodType(Void.TYPE, factorData.javaClass, GraphDataInput::class.java))
              .bindTo(factorData)
          }
          // first element
          @Suppress("UNCHECKED_CAST")
          result.add(processLoadedGraphElement(constructor.invoke(this) as T))
        }
      }
    }
    else {
      val constructor = lookup.findConstructor(elementType, defaultReadConstructorType)
      var size = readInt()
      while (size-- > 0) {
        @Suppress("UNCHECKED_CAST")
        result.add(processLoadedGraphElement(constructor.invoke(this) as T))
      }
    }
    return result
  }

  protected open fun <T : ExternalizableGraphElement> processLoadedGraphElement(element: T): T = element
}