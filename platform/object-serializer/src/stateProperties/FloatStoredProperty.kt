// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.*
import com.intellij.openapi.util.text.StringUtil

internal class FloatStoredProperty(private val defaultValue: Float, private val valueNormalizer: ((value: Float) -> Float)?) : StoredPropertyBase<Float>(), ScalarProperty {
  private var value = defaultValue

  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.NUMBER

  override fun getValue(thisRef: BaseState) = value

  override fun setValue(thisRef: BaseState, value: Float) {
    val newValue = valueNormalizer?.invoke(value) ?: value
    if (this.value != newValue) {
      thisRef.intIncrementModificationCount()
      this.value = newValue
    }
  }

  override fun setValue(other: StoredProperty<Float>): Boolean {
    val newValue = (other as FloatStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun equals(other: Any?) = this === other || (other is FloatStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = "$name = $value${if (value == defaultValue) " (default)" else ""}"

  override fun isEqualToDefault() = value == defaultValue

  override fun parseAndSetValue(rawValue: String?) {
    if (rawValue == null) {
      return
    }

    value = parseYamlFloat(rawValue)
  }
}

private fun parseYamlFloat(_value: String): Float {
  var value = StringUtil.replace(_value, "_", "")
  var sign = +1
  val first = value[0]
  if (first == '-') {
    sign = -1
    value = value.substring(1)
  }
  else if (first == '+') {
    value = value.substring(1)
  }

  return when {
    StringUtil.equalsIgnoreCase(".inf", value) -> if (sign == -1) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
    StringUtil.equalsIgnoreCase(".nan", value) -> Float.NaN
    value.indexOf(':') != -1 -> {
      val digits = value.split(":")
      var bes = 1
      var v = 0.0F
      var i = 0
      val j = digits.size
      while (i < j) {
        v += (digits[j - i - 1]).toFloat() * bes
        bes *= 60
        i++
      }
      sign * v
    }
    else -> value.toFloat() * sign
  }
}