// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.util

import java.util.*

class ArgMap<T : Enum<T>> internal constructor(private val map: EnumMap<T, MutableList<String>>) {
  fun mandatorySingle(key: T): String {
    val value = optionalSingle(key)
    requireNotNull(value) { "$key is not optional" }
    require(value.isNotBlank()) { "--$key should not be blank" }
    return value
  }

  fun optionalSingle(key: T): String? {
    return map[key]?.let {
      when (it.size) {
        0 -> throw IllegalArgumentException("$key did not have a value")
        1 -> it[0]
        else -> throw IllegalArgumentException("$key should have a single value: $it")
      }
    }
  }

  fun boolFlag(key: T): Boolean {
    val value = map[key] ?: return false
    return when (value.size) {
      0 -> true
      1 -> value[0].toBoolean()
      else -> throw IllegalArgumentException("$key should have a single value: $value")
    }
  }

  fun optional(key: T): List<String>? = map[key]

  fun optionalList(key: T): List<String> = map[key] ?: emptyList()

  override fun toString(): String {
    if (map.isEmpty()) {
      return "Args is empty"
    }

    return map.entries.joinToString(separator = "\n", prefix = "Args:\n", postfix = "") { (key, value) ->
      "  - $key: ${value.joinToString(separator = ", ", prefix = "[", postfix = "]")}"
    }
  }
}

fun <T : Enum<T>> createArgMap(
  args: List<String>,
  enumClass: Class<T>,
): ArgMap<T> {
  val result = EnumMap<T, MutableList<String>>(enumClass)
  val keyString = args.first().also { require(it.startsWith("--")) { "first arg must be a flag" } }
    .substring(2)
    .uppercase()
    .replace('-', '_')
  var currentKey = java.lang.Enum.valueOf(enumClass, keyString)
  val currentValue = mutableListOf<String>()

  fun mergeCurrent() {
    result.computeIfAbsent(currentKey) { mutableListOf() }.addAll(currentValue)
    currentValue.clear()
  }

  for (it in args.drop(1)) {
    if (it.startsWith("--")) {
      mergeCurrent()
      currentKey = java.lang.Enum.valueOf(enumClass, it.substring(2).uppercase().replace('-', '_'))
    }
    else {
      currentValue.add(it)
    }
  }
  mergeCurrent()
  return ArgMap(result)
}
