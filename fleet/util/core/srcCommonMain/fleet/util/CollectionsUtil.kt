// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.bifurcan.SortedMap
import fleet.util.logging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

inline fun <T> Iterable<T>.forEachSafely(logger: KLogger, f: (T) -> Unit) {
  forEach {
    try {
      f(it)
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }
}

fun <T, K> Iterable<T>.associateByUnique(keySelector: (T) -> K): Map<K, T> =
  let { iter ->
    buildMap {
      for (x in iter) {
        val key = keySelector(x)
        require(put(key, x) == null) { "key $key is not unique" }
      }
    }
  }

/**
 * Same idea as [kotlin.collections.singleOrNull] but will throw if the collection contains more than one element.
 * */
inline fun <T> Iterable<T>.singleOrNullOrThrow(p: (T) -> Boolean = { true }): T? {
  var single: T? = null
  var found = false
  for (element in this) {
    if (p(element)) {
      if (found) {
        throw IllegalArgumentException("Collection contains more than one matching element: $single, $element")
      }
      single = element
      found = true
    }
  }
  return single
}

inline fun <T> Iterable<T>.singleOrNullOrThrowWithMessage(errorMessage: () -> String): T? {
  var single: T? = null
  var found = false
  for (element in this) {
    if (found) {
      throw IllegalArgumentException("Collection contains more than one matching element: $single, $element ${errorMessage()}")
    }
    single = element
    found = true
  }
  return single
}

inline fun <T> Flow<T>.takeTillFirst(crossinline predicate: (T) -> Boolean): Flow<T> {
  return this@takeTillFirst.transformWhile {
    emit(it)
    !predicate(it)
  }
}

inline fun <T> Iterable<T>.takeTillFirst(crossinline predicate: (T) -> Boolean): Iterable<T> {
  val list = ArrayList<T>()
  for (item in this) {
    list.add(item)
    if (predicate(item)) break
  }
  return list
}

fun <T> IBifurcanVector<T>.asReversedSequence(): Sequence<T> {
  return ((size() - 1) downTo 0L).asSequence().map { nth(it) }
}

inline fun <T, K> Sequence<T>.chunkedBy(crossinline selector: (T) -> K): Sequence<List<T>> {
  return sequence {
    var lastList = mutableListOf<T>()
    var lastSelector: K? = null
    for (elem in this@chunkedBy) {
      val currentSelector = selector(elem)
      if (lastList.isNotEmpty() && currentSelector != lastSelector) {
        yield(lastList)
        lastList = mutableListOf()
      }
      lastList.add(elem)
      lastSelector = currentSelector
    }
    if (lastList.isNotEmpty()) {
      yield(lastList)
    }
  }
}

inline fun <T, K> Iterable<T>.chunkedBy(crossinline selector: (T) -> K): List<List<T>> {
  return asSequence().chunkedBy(selector).toList()
}

inline fun <T> Sequence<T>.mergedBy(crossinline selector: (T, T) -> Boolean): Sequence<List<T>> {
  var prev: T? = null
  var chunkId = -1
  return chunkedBy { curr ->
    @Suppress("UNCHECKED_CAST")
    if (chunkId == -1 || !selector(prev as T, curr)) chunkId++
    prev = curr
    chunkId
  }
}

fun <T> ListIterator<T>.backwards(): ListIterator<T> {
  return object : ListIterator<T> {
    override fun hasNext(): Boolean = this@backwards.hasPrevious()
    override fun hasPrevious(): Boolean = this@backwards.hasNext()
    override fun next(): T = this@backwards.previous()
    override fun previous(): T = this@backwards.next()
    override fun nextIndex(): Int = this@backwards.previousIndex()
    override fun previousIndex(): Int = this@backwards.nextIndex()
  }
}

fun <K, V> Map<K, V>.merge(other: Map<K, V>, f: (K, V, V) -> V = { k, v1, v2 -> v2 }): Map<K, V> {
  val x = toMutableMap()
  other.forEach { (k, v) ->
    when {
      x.containsKey(k) -> {
        x[k] = f(k, x[k] as V, v)
      }
      else -> x[k] = v
    }
  }
  return x
}

fun <T> List<T>.zipWithIndex(): List<IndexedValue<T>> {
  return zip(indices) { v, idx ->
    IndexedValue(idx, v)
  }
}

fun <T : Comparable<T>> max(c1: T, c2: T): T {
  val x = c1.compareTo(c2)
  return when {
    x == 0 -> c1
    x < 0 -> c2
    else -> c1
  }
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

/**
 * Replicates java's Unmodifiable set
 */
private class UnmodifiableSet<T>(private val set: Set<T>) : MutableSet<T>, Set<T> by set {
  override fun add(element: T): Boolean {
    throw UnsupportedOperationException()
  }

  override fun addAll(elements: Collection<T>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun clear() {
    throw UnsupportedOperationException()
  }

  override fun iterator(): MutableIterator<T> {
    val iterator = set.iterator()
    return object : MutableIterator<T> {
      override fun remove() {
        throw UnsupportedOperationException()
      }

      override fun next(): T = iterator.next()
      override fun hasNext(): Boolean = iterator.hasNext()
    }
  }

  override fun remove(element: T): Boolean {
    throw UnsupportedOperationException()
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    throw UnsupportedOperationException()
  }
}

fun <T> Set<T>.toUnmodifiableSet(): MutableSet<T> = UnmodifiableSet(this)