// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

private val associateNotNullToken = object {}

@Suppress("UNCHECKED_CAST")
fun <T, K, V> Iterable<T>.associateNotNull(valueSelector: (T) -> Pair<K, V>?): Map<K, V> =
  associate { valueSelector(it) ?: (associateNotNullToken to null) }
    .filterKeys { it != associateNotNullToken } as Map<K, V>

private val associateWithNotNullToken = object {}

@Suppress("UNCHECKED_CAST")
fun <K, V> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> =
  associateWith { valueSelector(it) ?: associateWithNotNullToken }
    .filterKeys { it != associateWithNotNullToken } as Map<K, V>

fun <T> Iterable<Set<T>>.union(): Set<T> = flatMapTo(mutableSetOf()) { it }

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.util.test"])
fun <T> MutableList<T>.updateGrouping(
  beforeGroup: MutableList<T>.(isOpening: Boolean) -> Unit = {},
  afterGroup: MutableList<T>.(isClosing: Boolean) -> Unit = {},
  body: MutableList<T>.(newGroup: () -> Unit) -> Unit,
) {
  beforeGroup(true)
  var count = size
  var lastGroupEnd = size
  body {
    if (count < size) {
      afterGroup(false)
      lastGroupEnd = size
      beforeGroup(false)
      count = size
    }
  }
  if (count == size)
    while (size > lastGroupEnd) { removeLast() }
  afterGroup(true)
}

fun <T> MutableList<T>.updateSeparating(
  separator: MutableList<T>.() -> Unit = {},
  body: MutableList<T>.(newGroup: () -> Unit) -> Unit,
) = updateGrouping(
  beforeGroup = { isOpening -> if (!isOpening) separator() },
) { newGroup ->
  newGroup()
  body(newGroup)
}