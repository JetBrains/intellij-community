// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import software.amazon.ion.IonType
import software.amazon.ion.Timestamp
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

private typealias NestedBindingFactory = (accessor: MutableAccessor) -> NestedBinding
private typealias RootBindingFactory = () -> RootBinding

internal class IonBindingProducer(override val propertyCollector: PropertyCollector) : BindingProducer<RootBinding>(), BindingInitializationContext {
  companion object {
    private val classToNestedBindingFactory = THashMap<Class<*>, NestedBindingFactory>(32, ContainerUtil.identityStrategy())
    private val classToRootBindingFactory = THashMap<Class<*>, RootBindingFactory>(32, ContainerUtil.identityStrategy())

    private fun resolved(binding: NestedBinding): NestedBindingFactory = { binding }

    init {
      // for root resolved factory doesn't make sense because root bindings will be cached
      classToRootBindingFactory.put(java.lang.String::class.java) { StringBinding() }
      classToRootBindingFactory.put(Date::class.java) { DateBinding() }
      classToRootBindingFactory.put(ByteArray::class.java) { ByteArrayBinding() }

      val map = classToNestedBindingFactory
      map.put(java.lang.Short.TYPE, resolved(ShortBinding()))
      map.put(Integer.TYPE, resolved(IntBinding()))
      map.put(java.lang.Long.TYPE, resolved(LongBinding()))

      map.put(java.lang.Float.TYPE, resolved(FloatBinding()))
      map.put(java.lang.Double.TYPE, resolved(DoubleBinding()))

      // java.lang.Float cannot be cast to java.lang.Double
      map.put(java.lang.Float::class.java, resolved(FloatAsObjectBinding()))
      map.put(java.lang.Double::class.java, resolved(DoubleAsObjectBinding()))

      map.put(java.lang.Boolean.TYPE, resolved(BooleanBinding()))
      map.put(java.lang.Boolean::class.java, resolved(BooleanAsObjectBinding()))

      val char: NestedBindingFactory = { throw UnsupportedOperationException("char is not supported") }
      map.put(Character.TYPE, char)
      map.put(Character::class.java, char)

      val byte: NestedBindingFactory = { throw UnsupportedOperationException("byte is not supported") }
      map.put(java.lang.Byte.TYPE, byte)
      map.put(java.lang.Byte::class.java, byte)

      classToRootBindingFactory.forEachEntry { key, factory ->
        map.put(key) { AccessorWrapperBinding(factory()) }
        true
      }
    }
  }

  override val bindingProducer: BindingProducer<RootBinding>
    get() = this

  override fun createCacheKey(aClass: Class<*>, originalType: Type): Type {
    if (aClass !== originalType && !Collection::class.java.isAssignableFrom(aClass) && !classToRootBindingFactory.contains(aClass)) {
      // type parameters for bean binding doesn't play any role, should be the only binding for such class
      return aClass
    }
    else {
      return originalType
    }
  }

  override fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type, map: MutableMap<Type, RootBinding>): RootBinding {
    val binding = if (Collection::class.java.isAssignableFrom(aClass)) {
      createCollectionBinding(type)
    }
    else {
      val custom = classToRootBindingFactory.get(aClass)?.invoke()
      @Suppress("IfThenToElvis")
      if (custom == null) {
        if (aClass.isArray) {
          ArrayBinding(aClass.componentType, this)
        }
        else {
          assert(cacheKey === aClass)
          BeanBinding(aClass)
        }
      }
      else {
        custom
      }
    }

    map.put(cacheKey, binding)
    try {
      binding.init(type, this)
    }
    catch (e: Throwable) {
      map.remove(type)
      throw e
    }
    return binding
  }

  // note about field name - Ion binary writer interns string automatically, no need to intern (text writer doesn't support symbol tables)
  override fun getNestedBinding(accessor: MutableAccessor): NestedBinding {
    val type = accessor.genericType
    val aClass = ClassUtil.typeToClass(type)

    // PrimitiveBinding can serialize conditionally, but for the sake of optimization, use special bindings to avoid comparison for each value
    // yes - if field typed as Object, Number and Boolean types are not supported (because bean binding will be created)
    classToNestedBindingFactory.get(aClass)?.let {
      return it(accessor)
    }

    // CollectionBinding implements NestedBinding directly because can mutate property value directly
    return when {
      Collection::class.java.isAssignableFrom(aClass) -> createCollectionBinding(type)
      aClass.isArray -> ArrayBinding(aClass.componentType, this)
      java.lang.Number::class.java.isAssignableFrom(aClass) -> NumberAsObjectBinding()
      aClass.isEnum -> throw UnsupportedOperationException("enum is not supported")
      else -> AccessorWrapperBinding(getRootBinding(aClass, type))
    }
  }

  private fun createCollectionBinding(type: Type): CollectionBinding {
    return CollectionBinding(ClassUtil.typeToClass((type as ParameterizedType).actualTypeArguments[0]), this)
  }
}

private class FloatAsObjectBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      writeFloat((it as Float).toDouble())
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      doubleValue().toFloat()
    }
  }
}

private class DoubleAsObjectBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      writeFloat(it as Double)
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      doubleValue()
    }
  }
}

private class NumberAsObjectBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      writeInt((it as Number).toLong())
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      intValue()
    }
  }
}

private class BooleanAsObjectBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      writeBool(it as Boolean)
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      booleanValue()
    }
  }
}

private class BooleanBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeBool(property.readBoolean(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setBoolean(hostObject, context.reader.booleanValue())
  }
}

private open class IntBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeInt(property.readInt(hostObject).toLong())
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setInt(hostObject, context.reader.intValue())
  }
}

private class ShortBinding : IntBinding() {
  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setShort(hostObject, context.reader.intValue().toShort())
  }
}

private class LongBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeInt(property.readLong(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setLong(hostObject, context.reader.longValue())
  }
}

private class FloatBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeFloat(property.readFloat(hostObject).toDouble())
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setFloat(hostObject, context.reader.doubleValue().toFloat())
  }
}

private class DoubleBinding : NestedBinding {
  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeFloat(property.readDouble(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setDouble(hostObject, context.reader.doubleValue())
  }
}

// todo intern short strings?
private class StringBinding : RootBinding {
  override fun deserialize(context: ReadContext): Any {
    return context.reader.stringValue()
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeString(obj as String)
  }
}

private class DateBinding : RootBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeTimestamp(Timestamp.forDateZ(obj as Date))
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.dateValue()
  }
}

internal inline fun write(hostObject: Any, accessor: MutableAccessor, context: WriteContext, write: ValueWriter.(value: Any) -> Unit) {
  val value = accessor.read(hostObject)
  if (context.filter.isSkipped(value)) {
    return
  }

  val writer = context.writer
  writer.setFieldName(accessor.name)
  if (value == null) {
    writer.writeNull()
  }
  else {
    writer.write(value)
  }
}

internal inline fun read(hostObject: Any, property: MutableAccessor, context: ReadContext, read: ValueReader.() -> Any) {
  val type = context.reader.type
  if (type == IonType.NULL) {
    property.set(hostObject, null)
  }
  else {
    property.set(hostObject, context.reader.read())
  }
}

private class ByteArrayBinding : RootBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeBlob(obj as ByteArray)
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.newBytes()
  }
}