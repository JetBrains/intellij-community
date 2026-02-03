// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StatePropertyFactory
import com.intellij.openapi.components.StoredPropertyBase
import java.util.*

internal class StatePropertyFactoryImpl : StatePropertyFactory {
  override fun bool(defaultValue: Boolean) = ObjectStoredProperty(defaultValue)

  override fun <T> obj(defaultValue: T) = ObjectStoredProperty(defaultValue)

  override fun <T> obj(initialValue: T, isDefault: (value: T) -> Boolean): StoredPropertyBase<T> {
    return object : ObjectStoredProperty<T>(initialValue) {
      override fun isEqualToDefault() = isDefault(value)
    }
  }

  override fun <T : BaseState?> stateObject(initialValue: T) = StateObjectStoredProperty(initialValue)

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> list(): StoredPropertyBase<MutableList<T>> = ListStoredProperty<T>() as StoredPropertyBase<MutableList<T>>

  override fun <K : Any, V : Any> map(value: MutableMap<K, V>?) = MapStoredProperty(value)

  override fun string(defaultValue: String?): StoredPropertyBase<String?> = NormalizedStringStoredProperty(defaultValue)

  override fun float(defaultValue: Float, valueNormalizer: ((value: Float) -> Float)?) = FloatStoredProperty(defaultValue, valueNormalizer)

  override fun long(defaultValue: Long) = LongStoredProperty(defaultValue, null)

  override fun int(defaultValue: Int) = IntStoredProperty(defaultValue, null)

  override fun stringSet(defaultValue: String?): CollectionStoredProperty<String, MutableSet<String>> {
    val collection = HashSet<String>()
    defaultValue?.let {
      collection.add(defaultValue)
    }
    return CollectionStoredProperty(collection, defaultValue)
  }

  override fun <E> treeSet(): StoredPropertyBase<MutableSet<E>> where E : Comparable<E>, E : BaseState = CollectionStoredProperty(TreeSet(), null)

  override fun <T : Enum<*>> enum(defaultValue: T?, clazz: Class<T>): StoredPropertyBase<T?> = EnumStoredProperty(defaultValue, clazz)
}