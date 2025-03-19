// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

fun <K, V> MutableMap<K, V>.computeIfAbsentShim(key: K, f: (K) -> V): V {
  return get(key) ?: f(key).also { set(key, it) }
}

fun <K, V> MutableMap<K, V>.putIfAbsentShim(key: K, value: V): V {
  return computeIfAbsentShim(key) { value }
}

fun <K, V> MutableMap<K, V>.computeShim(key: K, f: (K, V?) -> V?): V? {
  val result = f(key, get(key))
  if (result != null) {
    set(key, result)
  } else {
    remove(key)
  }
  return result
}

fun <K, V> MutableMap<K, V>.removeShim(key: K, value: V): Boolean {
  return if (get(key) == value) {
    remove(key)
    true
  } else {
    false
  }
}