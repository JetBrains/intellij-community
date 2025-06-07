// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs


fun <V> LongMap<V>.filterNot(predicate: (LongEntry<V>) -> Boolean): Map<Long, V> {
  val dest = LinkedHashMap<Long, V>()
  for (element in entries) {
    if (!predicate(element)) {
      dest[element.key] = element.value
    }
  }
  return dest
}

fun <V> LongMap<V>.valuesToHashSet(): Set<V> {
  val res = HashSet<V>()
  for (entry in entries) {
    res.add(entry.value)
  }
  return res
}