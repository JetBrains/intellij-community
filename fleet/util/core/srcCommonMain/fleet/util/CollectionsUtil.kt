// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

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

inline fun <T> Iterable<T>.takeTillFirst(crossinline predicate: (T) -> Boolean): Iterable<T> {
  val list = ArrayList<T>()
  for (item in this) {
    list.add(item)
    if (predicate(item)) break
  }
  return list
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

@Deprecated(replaceWith = ReplaceWith("maxOf(c1, c2)"), message = "use kotlin stdlib")
fun <T : Comparable<T>> max(c1: T, c2: T): T {
  return maxOf(c1, c2)
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