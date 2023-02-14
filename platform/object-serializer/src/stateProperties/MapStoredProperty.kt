// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.JsonSchemaType
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectSet

class MapStoredProperty<K: Any, V>(value: MutableMap<K, V>?) : StoredPropertyBase<MutableMap<K, V>>() {
  private val value: MutableMap<K, V> = value ?: MyMap()

  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.OBJECT

  override fun isEqualToDefault() = value.isEmpty()

  override fun getValue(thisRef: BaseState) = value

  override fun setValue(thisRef: BaseState, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: MutableMap<K, V>) {
    if (doSetValue(value, newValue)) {
      thisRef.intIncrementModificationCount()
    }
  }

  private fun doSetValue(old: MutableMap<K, V>, new: Map<K, V>): Boolean {
    if (old == new) {
      return false
    }

    old.clear()
    old.putAll(new)
    return true
  }

  override fun equals(other: Any?) = this === other || (other is MapStoredProperty<*, *> && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = if (isEqualToDefault()) "" else value.toString()

  override fun setValue(other: StoredProperty<MutableMap<K, V>>): Boolean {
    return doSetValue(value, (other as MapStoredProperty<K, V>).value)
  }

  @Suppress("FunctionName")
  fun __getValue() = value

  override fun getModificationCount(): Long {
    return when (value) {
      is MyMap -> value.modificationCount
      else -> {
        var result = 0L
        for (value in value.values) {
          if (value is BaseState) {
            result += value.modificationCount
          }
          else {
            // or all values are BaseState or not
            break
          }
        }
        result
      }
    }
  }
}

private class MyMap<K: Any, V> : Object2ObjectOpenHashMap<K, V>() {
  @Volatile
  var modificationCount = 0L

  //this override is needed to ensure that Kotlin compiler won't generate incorrect bridge methods which would lead to StackOverflowError (KT-48167)
  @Suppress("UNCHECKED_CAST")
  override val entries: ObjectSet<MutableMap.MutableEntry<K, V>>
    get() = object2ObjectEntrySet() as ObjectSet<MutableMap.MutableEntry<K, V>>

  override fun put(key: K, value: V): V? {
    val oldValue = super.put(key, value)
    if (oldValue !== value) {
      modificationCount++
    }
    return oldValue
  }

  // to detect a remove from iterator
  override fun remove(key: K): V? {
    val result = super.remove(key)
    modificationCount++
    return result
  }

  override fun clear() {
    super.clear()
    modificationCount++
  }
}