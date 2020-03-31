// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization.stateProperties

import com.intellij.openapi.components.*
import com.intellij.openapi.util.text.StringUtil

internal class LongStoredProperty(private val defaultValue: Long, private val valueNormalizer: ((value: Long) -> Long)?) : StoredPropertyBase<Long>(), ScalarProperty {
  private var value = defaultValue

  override val jsonType: JsonSchemaType
    get() = JsonSchemaType.INTEGER

  override fun getValue(thisRef: BaseState) = value

  override fun setValue(thisRef: BaseState, value: Long) {
    val newValue = valueNormalizer?.invoke(value) ?: value
    if (this.value != newValue) {
      thisRef.intIncrementModificationCount()
      this.value = newValue
    }
  }

  override fun setValue(other: StoredProperty<Long>): Boolean {
    val newValue = (other as LongStoredProperty).value
    if (newValue == value) {
      return false
    }

    value = newValue
    return true
  }

  override fun equals(other: Any?) = this === other || (other is LongStoredProperty && value == other.value)

  override fun hashCode() = value.hashCode()

  override fun toString() = "$name = $value${if (value == defaultValue) " (default)" else ""}"

  override fun isEqualToDefault() = value == defaultValue

  override fun parseAndSetValue(rawValue: String?) {
    if (rawValue == null) {
      return
    }

    value = parseYamlLong(rawValue)
  }
}

private fun parseYamlLong(_value: String): Long {
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

  if ("0" == value) {
    return 0
  }

  return when {
    value.startsWith("0b") -> {
      createNumber(sign, value.substring(2), 2)
    }

    value.startsWith("0x") -> {
      createNumber(sign, value.substring(2), 16)
    }

    value.startsWith("0") -> {
      createNumber(sign, value.substring(1), 6)
    }

    value.indexOf(':') != -1 -> {
      val digits = value.split(":")
      var bes = 1
      var v = 0L
      var i = 0
      val j = digits.size
      while (i < j) {
        v += ((digits[j - i - 1]).toLong() * bes)
        bes *= 60
        i++
      }
      createNumber(sign, v.toString(), 10)
    }
    else -> createNumber(sign, value, 10)
  }
}

private fun createNumber(sign: Int, _number: String, radix: Int): Long {
  var number = _number
  if (sign < 0) {
    number = "-$number"
  }
  return number.toLong(radix)
}