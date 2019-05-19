// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.intellij.util.ArrayUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import gnu.trove.THashSet
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.function.Consumer

internal abstract class BaseCollectionBinding(itemType: Type, context: BindingInitializationContext) : Binding {
  private val itemBinding = createElementBindingByType(itemType, context)

  protected fun createItemConsumer(context: WriteContext): Consumer<Any?> {
    val writer = context.writer
    return Consumer {
      if (it == null) {
        writer.writeNull()
      }
      else {
        itemBinding.serialize(it, context)
      }
    }
  }

  fun readInto(result: MutableCollection<Any?>, context: ReadContext) {
    val reader = context.reader
    reader.stepIn()
    while (true) {
      @Suppress("MoveVariableDeclarationIntoWhen")
      val type = reader.next() ?: break
      val item = when (type) {
        IonType.NULL -> null
        else -> itemBinding.deserialize(context)
      }
      result.add(item)
    }
    reader.stepOut()
  }
}

internal class CollectionBinding(type: ParameterizedType, context: BindingInitializationContext) : BaseCollectionBinding(type.actualTypeArguments[0], context) {
  private val collectionClass = ClassUtil.typeToClass(type)

  override fun deserialize(context: ReadContext): Collection<Any?> {
    if (context.reader.type == IonType.INT) {
      LOG.assertTrue(context.reader.intValue() == 0)
      return if (Set::class.java.isAssignableFrom(collectionClass)) emptySet() else emptyList()
    }

    val result = createCollection()
    readInto(result, context)
    return result
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    val collection = obj as Collection<*>
    if (context.filter.skipEmptyCollection && collection.isEmpty()) {
      // some value must be written otherwise on deserialize null will be used for constructor parameters (and it can be not expected)
      writer.writeInt(0)
      return
    }

    writer.stepIn(IonType.LIST)
    collection.forEach(createItemConsumer(context))
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    val type = context.reader.type
    if (type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }
    else if (type == IonType.INT /* empty collection if context.filter.skipEmptyCollection */) {
      return
    }

    @Suppress("UNCHECKED_CAST")
    var result = property.readUnsafe(hostObject) as MutableCollection<Any?>?
    if (result != null && ClassUtil.isMutableCollection(result)) {
      result.clear()
    }
    else {
      result = createCollection()
      property.set(hostObject, result)
    }
    readInto(result, context)
  }

  private fun createCollection(propertyForDebugPurposes: MutableAccessor? = null): MutableCollection<Any?> {
    if (collectionClass.isInterface) {
      when (collectionClass) {
        Set::class.java -> return THashSet()
        List::class.java, Collection::class.java -> return ArrayList()
        else -> LOG.warn("Unknown collection type interface: ${collectionClass} (property: $propertyForDebugPurposes)")
      }
    }
    else {
      return when (collectionClass) {
        THashSet::class.java -> THashSet()
        HashSet::class.java -> HashSet()
        ArrayList::class.java -> ArrayList()
        SmartList::class.java -> SmartList()
        else -> {
          @Suppress("UNCHECKED_CAST")
          ReflectionUtil.newInstance(collectionClass, false) as MutableCollection<Any?>
        }
      }
    }

    return ArrayList()
  }
}

internal class ArrayBinding(private val itemClass: Class<*>, context: BindingInitializationContext) : BaseCollectionBinding(itemClass, context) {
  override fun deserialize(context: ReadContext) = readArray(context)

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    val type = context.reader.type
    if (type == IonType.NULL) {
      property.set(hostObject, null)
    }
    else if (type != IonType.INT) {
      property.set(hostObject, readArray(context))
    }
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val array = obj as Array<*>
    val writer = context.writer
    if (context.filter.skipEmptyArray && array.isEmpty()) {
      writer.writeInt(0)
      return
    }

    writer.stepIn(IonType.LIST)
    val consumer = createItemConsumer(context)
    array.forEach { consumer.accept(it) }
    writer.stepOut()
  }

  private fun readArray(context: ReadContext): Array<out Any> {
    val list = ArrayList<Any?>()
    readInto(list, context)
    val result = ArrayUtil.newArray(itemClass, list.size)
    list.toArray(result)
    return result
  }
}