// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints

fun <V> IntMap<V>.filterNot(predicate: (IntEntry<V>) -> Boolean): Map<Int, V> {
  val dest = LinkedHashMap<Int, V>()
  for (element in entries) {
    if (!predicate(element)) {
      dest[element.key] = element.value
    }
  }
  return dest
}

fun <V> IntMap<V>.valuesToHashSet(): Set<V> {
  val res = HashSet<V>()
  for (entry in entries) {
    res.add(entry.value)
  }
  return res
}
