// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import gnu.trove.THashSet
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

// marker value of collection that skipped because empty
private const val EMPTY_SKIPPED_COLLECTION = 0

private const val EMPTY_JAVA_LIST = 1
private const val EMPTY_JAVA_SET = 2
private const val EMPTY_KOTLIN_SET = 4
private const val EMPTY_KOTLIN_LIST = 3

internal class CollectionBinding(type: ParameterizedType, context: BindingInitializationContext) : BaseCollectionBinding(type.actualTypeArguments[0], context) {
  private val collectionClass = ClassUtil.typeToClass(type)

  override fun deserialize(context: ReadContext, hostObject: Any?): Collection<Any?> {
    if (context.reader.type == IonType.INT) {
      return readEmptyCollection(context)
    }

    val result = createCollection()
    readInto(hostObject, result, context)
    return result
  }

  private fun readEmptyCollection(context: ReadContext): Collection<Any?> {
    return when (context.reader.intValue()) {
      EMPTY_JAVA_LIST -> Collections.EMPTY_LIST
      EMPTY_KOTLIN_LIST -> emptyList()
      EMPTY_JAVA_SET -> Collections.EMPTY_SET
      EMPTY_KOTLIN_SET -> emptySet()
      else -> if (Set::class.java.isAssignableFrom(collectionClass)) emptySet() else emptyList()
    }
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    val collection = obj as Collection<*>

    if (context.filter.skipEmptyCollection && collection.isEmpty()) {
      // some value must be written otherwise on deserialize null will be used for constructor parameters (and it can be not expected)
      writer.writeInt(EMPTY_SKIPPED_COLLECTION.toLong())
      return
    }

    when {
      collection === Collections.EMPTY_LIST -> writer.writeInt(EMPTY_JAVA_LIST.toLong())
      collection === emptyList<Any>() -> writer.writeInt(EMPTY_KOTLIN_LIST.toLong())
      collection === Collections.EMPTY_SET -> writer.writeInt(EMPTY_JAVA_SET.toLong())
      collection === emptyList<Any>() -> writer.writeInt(EMPTY_KOTLIN_SET.toLong())
      else -> {
        writer.stepIn(IonType.LIST)
        collection.forEach(createItemConsumer(context))
        writer.stepOut()
      }
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    val type = context.reader.type
    var emptyResult: Collection<Any?>? = null
    if (type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }
    else if (type == IonType.INT) {
      emptyResult = readEmptyCollection(context)
    }

    @Suppress("UNCHECKED_CAST")
    var result = property.readUnsafe(hostObject) as? MutableCollection<Any?>?
    if (result != null && ClassUtil.isMutableCollection(result)) {
      result.clear()
      if (emptyResult != null) {
        return
      }
    }
    else if (emptyResult != null) {
      property.set(hostObject, emptyResult)
      return
    }
    else {
      result = createCollection()
      property.set(hostObject, result)
    }
    readInto(hostObject, result, context)
  }

  private fun createCollection(propertyForDebugPurposes: MutableAccessor? = null): MutableCollection<Any?> {
    if (collectionClass.isInterface) {
      when (collectionClass) {
        Set::class.java -> return HashSet()
        List::class.java, Collection::class.java -> return ArrayList()
        else -> LOG.warn("Unknown collection type interface: ${collectionClass} (property: $propertyForDebugPurposes)")
      }
    }
    else {
      return when (collectionClass) {
        HashSet::class.java -> HashSet()
        ArrayList::class.java -> ArrayList()
        THashSet::class.java -> THashSet()
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