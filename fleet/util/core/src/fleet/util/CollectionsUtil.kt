// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.logging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

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

inline fun <T, K> Iterable<T>.chunkedBy(crossinline selector: (T) -> K): List<List<T>> {
  val result = mutableListOf<List<T>>()
  var lastList = mutableListOf<T>()
  var lastSelector: K? = null
  for (elem in this) {
    val currentSelector = selector(elem)
    if (lastList.isNotEmpty() && currentSelector != lastSelector) {
      result.add(lastList)
      lastList = mutableListOf()
    }
    lastList.add(elem)
    lastSelector = currentSelector
  }
  result.add(lastList)
  return result
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
