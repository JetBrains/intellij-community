// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import java.lang.reflect.Type

private fun resolved(binding: Binding): NestedBindingFactory = { binding }

internal fun registerPrimitiveBindings(classToRootBindingFactory: MutableMap<Class<*>, RootBindingFactory>, classToNestedBindingFactory: MutableMap<Class<*>, NestedBindingFactory>) {
  classToRootBindingFactory.put(java.lang.String::class.java) { StringBinding() }

  val numberAsObjectBinding = NumberAsObjectBinding()
  classToRootBindingFactory.put(java.lang.Integer::class.java) { numberAsObjectBinding }
  classToRootBindingFactory.put(java.lang.Long::class.java) { numberAsObjectBinding }
  classToRootBindingFactory.put(java.lang.Short::class.java) { numberAsObjectBinding }

  // java.lang.Float cannot be cast to java.lang.Double
  classToRootBindingFactory.put(java.lang.Float::class.java) { FloatAsObjectBinding() }
  classToRootBindingFactory.put(java.lang.Double::class.java) { DoubleAsObjectBinding() }
  classToRootBindingFactory.put(java.lang.Boolean::class.java) { BooleanAsObjectBinding() }

  classToNestedBindingFactory.put(java.lang.Short.TYPE, resolved(ShortBinding()))
  classToNestedBindingFactory.put(Integer.TYPE, resolved(IntBinding()))
  classToNestedBindingFactory.put(java.lang.Long.TYPE, resolved(LongBinding()))

  classToNestedBindingFactory.put(java.lang.Float.TYPE, resolved(FloatBinding()))
  classToNestedBindingFactory.put(java.lang.Double.TYPE, resolved(DoubleBinding()))

  classToNestedBindingFactory.put(java.lang.Boolean.TYPE, resolved(BooleanBinding()))

  val char: NestedBindingFactory = { throw UnsupportedOperationException("char is not supported") }
  classToNestedBindingFactory.put(Character.TYPE, char)
  classToNestedBindingFactory.put(Character::class.java, char)

  val byte: NestedBindingFactory = { throw UnsupportedOperationException("byte is not supported") }
  classToNestedBindingFactory.put(java.lang.Byte.TYPE, byte)
  classToNestedBindingFactory.put(java.lang.Byte::class.java, byte)
}

private class FloatAsObjectBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeFloat((obj as Float).toDouble())
  }

  override fun deserialize(context: ReadContext) = context.reader.doubleValue().toFloat()
}

private class DoubleAsObjectBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeFloat(obj as Double)
  }

  override fun deserialize(context: ReadContext) = context.reader.doubleValue()
}

internal class NumberAsObjectBinding : Binding {
  override fun createCacheKey(aClass: Class<*>?, type: Type) = aClass!!

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeInt((obj as Number).toLong())
  }

  override fun deserialize(context: ReadContext) = context.reader.intValue()
}

private class BooleanAsObjectBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeBool(obj as Boolean)
  }

  override fun deserialize(context: ReadContext) = context.reader.booleanValue()
}

private class BooleanBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeBool(obj as Boolean)
  }

  override fun deserialize(context: ReadContext) = context.reader.booleanValue()

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeBool(property.readBoolean(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setBoolean(hostObject, context.reader.booleanValue())
  }
}

private open class IntBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeInt(obj as Long)
  }

  override fun deserialize(context: ReadContext) = context.reader.intValue()

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

private class LongBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeInt(obj as Long)
  }

  override fun deserialize(context: ReadContext) = context.reader.longValue()

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeInt(property.readLong(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setLong(hostObject, context.reader.longValue())
  }
}

private class FloatBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeFloat(obj as Double)
  }

  override fun deserialize(context: ReadContext) = context.reader.doubleValue()

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeFloat(property.readFloat(hostObject).toDouble())
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setFloat(hostObject, context.reader.doubleValue().toFloat())
  }
}

private class DoubleBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeFloat(obj as Double)
  }

  override fun deserialize(context: ReadContext) = context.reader.doubleValue()

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    val writer = context.writer
    writer.setFieldName(property.name)
    writer.writeFloat(property.readDouble(hostObject))
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    property.setDouble(hostObject, context.reader.doubleValue())
  }
}

internal class StringBinding : Binding {
  override fun deserialize(context: ReadContext): Any {
    return context.reader.stringValue()
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val s = obj as String
    if (s.length < 64) {
      context.writer.writeSymbol(s)
    }
    else {
      context.writer.writeString(s)
    }
  }
}