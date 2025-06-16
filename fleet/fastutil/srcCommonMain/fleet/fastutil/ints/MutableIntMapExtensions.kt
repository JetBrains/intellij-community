// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints


fun <V> MutableIntMap<V>.getOrPut(key: Int, defaultValue: () -> V): V {
  val value = get(key)
  return if (value == null) {
    val answer = defaultValue()
    this.put(key, answer)
    answer
  }
  else {
    value
  }
}

inline fun <V> MutableIntMap<V>.removeIf(key: Int, p: (V) -> Boolean) {
  get(key)?.let { value ->
    if (p(value)) {
      remove(key)
    }
  }
}