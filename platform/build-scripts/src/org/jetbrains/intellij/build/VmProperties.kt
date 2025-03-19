// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.util.*

@JvmInline
value class VmProperties(val map: Map<String, String>) {
  inline fun mutate(operation: MutableMap<String, String>.() -> Unit): VmProperties {
    val map = LinkedHashMap(map)
    map.operation()
    return VmProperties(map.takeIf { it.isNotEmpty() } ?: Collections.emptyMap())
  }

  fun toJvmArgs(): List<String> {
    if (map.isEmpty()) {
      return Collections.emptyList()
    }

    val result = ArrayList<String>(map.size)
    for ((key, value) in map) {
      result.add("-D$key=$value")
    }
    return result
  }

  operator fun plus(other: VmProperties): VmProperties {
    return when {
      map.isEmpty() -> other
      other.map.isEmpty() -> this
      else -> VmProperties(map + other.map)
    }
  }
}