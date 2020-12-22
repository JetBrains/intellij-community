// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.JsonSchemaType
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import gnu.trove.THashMap

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
    @Suppress("UNCHECKED_CAST")
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

private class MyMap<K: Any, V> : THashMap<K, V>() {
  @Volatile
  var modificationCount = 0L

  override fun put(key: K, value: V): V? {
    val oldValue = super.put(key, value)
    if (oldValue !== value) {
      modificationCount++
    }
    return oldValue
  }

  // to detect a remove from iterator
  override fun removeAt(index: Int) {
    super.removeAt(index)
    modificationCount++
  }

  override fun clear() {
    super.clear()
    modificationCount++
  }
}