// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.bifurcan

import kotlin.collections.List


typealias BifurcanVector<T> = fleet.bifurcan.List<T>
typealias IBifurcanVector<T> = fleet.bifurcan.List<T>

fun <V> List<V>.toBifurcan(): BifurcanVector<V> {
  return BifurcanVector.from(this)
}

fun <T> IBifurcanVector<T>.asReversedSequence(): Sequence<T> {
  return ((size() - 1) downTo 0L).asSequence().map { nth(it) }
}


fun <T> Iterable<T>.toSortedSet(comparator: Comparator<T>): SortedSet<T> {
  val result = SortedMap<T, Unit?>(comparator).linear()
  forEach { e -> result.put(e, Unit) }
  return SortedSet<T>(result.forked())
}

fun <T : Comparable<T>> Iterable<T>.toSortedSet(): SortedSet<T> {
  val result = SortedMap<T, Unit?>().linear()
  forEach { e -> result.put(e, Unit) }
  return SortedSet<T>(result.forked())
}


fun <K : Comparable<K>, V> Map<K, V>.toSortedMap(): SortedMap<K, V> {
  return SortedMap.from(this)
}

fun <K, V> Map<out K, V>.toSortedMap(comparator: Comparator<K>): SortedMap<K, V> {
  val result = SortedMap<K, V>(comparator).linear()
  forEach { (key, value) -> result.put(key, value) }
  return result.forked()
}


fun <K : Comparable<K>, V> sortedMapOf(vararg pairs: Pair<K, V>): SortedMap<K, V> {
  val result = SortedMap<K, V>().linear()
  pairs.forEach { (key, value) -> result.put(key, value) }
  return result.forked()
}

fun <K : Comparable<K>, V> sortedMapOf(comparator: Comparator<K>): SortedMap<K, V> = SortedMap<K, V>(comparator)

inline fun <K, V, R, M : SortedMap<in K, in R>> Map<out K, V>.mapValuesTo(destination: M, transform: (Map.Entry<K, V>) -> R): M {
  return entries.associateByTo(destination, { it.key }, transform)
}

inline fun <T, K, V, M : SortedMap<in K, in V>> Iterable<T>.associateByTo(destination: M, keySelector: (T) -> K, valueTransform: (T) -> V): M {
  val forked = destination.linear()
  for (element in this) {
    forked.put(keySelector(element), valueTransform(element))
  }

  @Suppress("UNCHECKED_CAST")
  return forked.forked() as M
}
