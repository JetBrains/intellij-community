// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.JsonSchemaType
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.components.StoredPropertyBase
import com.intellij.util.SmartList

// Technically, it is not possible to proxy write operations because a collection can be mutated via iterator.
// So, even if Kotlin could create a delegate for us, to track mutations via an iterator we have to re-implement collection/map.

/**
 * `AbstractCollectionBinding` modifies collection directly, so we cannot use `null` as a default value and have to return an empty list.
 */
open class CollectionStoredProperty<E : Any, C : MutableCollection<E>>(protected val value: C) : StoredPropertyBase<C>() {
  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.ARRAY

  override fun isEqualToDefault() = value.isEmpty()

  override fun getValue(thisRef: BaseState) = value

  override fun setValue(thisRef: BaseState, @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") newValue: C) {
    if (doSetValue(value, newValue)) {
      thisRef.intIncrementModificationCount()
    }
  }

  private fun doSetValue(old: C, new: C): Boolean {
    if (old == new) {
      return false
    }

    old.clear()
    old.addAll(new)
    return true
  }

  override fun equals(other: Any?) = this === other || (other is CollectionStoredProperty<*, *> && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = "$name = ${if (isEqualToDefault()) "" else value.joinToString(" ")}"

  override fun setValue(other: StoredProperty<C>): Boolean {
    @Suppress("UNCHECKED_CAST")
    return doSetValue(value, (other as CollectionStoredProperty<E, C>).value)
  }

  @Suppress("FunctionName")
  fun __getValue() = value
}

internal class ListStoredProperty<T : Any> : CollectionStoredProperty<T, SmartList<T>>(SmartList()) {
  override fun getModificationCount() = value.modificationCount.toLong()
}