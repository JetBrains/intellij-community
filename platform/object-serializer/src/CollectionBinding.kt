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

internal abstract class BaseCollectionBinding(itemType: Type, context: BindingInitializationContext) : RootBinding, NestedBinding {
  private val itemBinding = createElementBindingByType(itemType, context)

  final override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      serialize(it, context)
    }
  }

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
    val result = createCollection()
    readInto(result, context)
    return result
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    val collection = obj as Collection<*>
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

    @Suppress("UNCHECKED_CAST")
    var result = property.read(hostObject) as MutableCollection<Any?>?
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

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    writer.stepIn(IonType.LIST)
    val consumer = createItemConsumer(context)
    (obj as Array<*>).forEach { consumer.accept(it) }
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      readArray(context)
    }
  }

  private fun readArray(context: ReadContext): Array<out Any> {
    val list = ArrayList<Any?>()
    readInto(list, context)
    val result = ArrayUtil.newArray(itemClass, list.size)
    list.toArray(result)
    return result
  }
}