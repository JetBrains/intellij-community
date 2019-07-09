// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.*

internal class NormalizedStringStoredProperty(private val defaultValue: String?) : StoredPropertyBase<String?>(), ScalarProperty {
  private var value = defaultValue

  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.STRING

  override fun getValue(thisRef: BaseState) = value

  override fun setValue(thisRef: BaseState, value: String?) {
    val newValue = if (value.isNullOrEmpty()) null else value
    if (this.value != newValue) {
      thisRef.intIncrementModificationCount()
      this.value = newValue
    }
  }

  override fun setValue(other: StoredProperty<String?>): Boolean {
    val newValue = (other as NormalizedStringStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun equals(other: Any?) = this === other || (other is NormalizedStringStoredProperty && value == other.value)

  override fun hashCode() = value?.hashCode() ?: 0

  override fun isEqualToDefault() = value == defaultValue

  override fun toString() = "$name = $value${if (value == defaultValue) " (default)" else ""}"

  override fun parseAndSetValue(rawValue: String?) {
    value = rawValue
  }
}