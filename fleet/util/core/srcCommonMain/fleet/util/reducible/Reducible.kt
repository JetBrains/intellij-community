// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.reducible

import kotlin.jvm.JvmInline

@JvmInline
value class ReduceDecision(private val continueReduce: Boolean) {
  companion object {
    val Continue = ReduceDecision(true)
    val Stop = ReduceDecision(false)
  }
}

interface Reducer<in T> {
  fun emit(t: T): ReduceDecision
}

interface Reducible<out T> {
  fun reduce(reducer: Reducer<T>): ReduceDecision
}

inline fun <T> Reducible<T>.forEach(crossinline f: (T) -> ReduceDecision): ReduceDecision {
  return reduce(object : Reducer<T> {
    override fun emit(t: T): ReduceDecision {
      return f(t)
    }
  })
}

fun <T> Reducer<T>.emitAll(x: Reducible<T>): ReduceDecision {
  return x.forEach { t ->
    emit(t)
  }
}

fun <T> reducible(f: Reducer<T>.() -> ReduceDecision): Reducible<T> {
  return object : Reducible<T> {
    override fun reduce(reducer: Reducer<T>): ReduceDecision {
      return reducer.f()
    }
  }
}

fun <T> emptyReducible(): Reducible<T> = reducible { ReduceDecision.Continue }
fun <T> reducibleOf(t: T): Reducible<T> = reducible { emit(t) }

fun <T, U> Reducible<T>.map(f: (T) -> U): Reducible<U> =
  reducible {
    forEach { t ->
      emit(f(t))
    }
  }

fun <T, U> Reducible<T>.mapNotNull(f: (T) -> U?): Reducible<U> =
  reducible {
    forEach { t ->
      val x = f(t)
      if (x != null) {
        emit(x)
      }
      else {
        ReduceDecision.Continue
      }
    }
  }


fun <T, U> Reducible<T>.flatMap(f: (T) -> Reducible<U>): Reducible<U> =
  reducible {
    forEach { t ->
      emitAll(f(t))
    }
  }

fun <T> Iterable<T>.reducible(): Reducible<T> {
  val iterable = this
  return reducible {
    var res = ReduceDecision.Continue
    for (x in iterable) {
      res = emit(x)
      if (res == ReduceDecision.Stop) break
    }
    res
  }
}

fun <T> Reducible<T>.firstOrNull(p: (T) -> Boolean = { true }): T? {
  var res: T? = null
  forEach { t ->
    if (p(t)) {
      res = t
      ReduceDecision.Stop
    }
    else {
      ReduceDecision.Continue
    }
  }
  return res
}

fun <T, R: Comparable<R>> Reducible<T>.maxBy(selector: (T) -> R): T? {
  var res: T? = null
  var maxValue: R? = null
  forEach { t ->
    val v = selector(t)
    if (maxValue == null || v > maxValue!!) {
      res = t
      maxValue = v
    }
    ReduceDecision.Continue
  }
  return res
}

fun <T> Reducible<T>.first(p: (T) -> Boolean = { true }): T = firstOrNull(p) ?: error("reducible is empty")

fun <T> Reducible<T>.take(count: Int): Reducible<T> {
  if (count < 0) {
    throw IllegalArgumentException("requested negative count: ${count}")
  }
  val red = this
  return reducible {
    var count = count
    red.forEach { x ->
      if (emit(x) == ReduceDecision.Stop || --count == 0) {
        ReduceDecision.Stop
      }
      else {
        ReduceDecision.Continue
      }
    }
  }
}

fun <T> Reducible<T>.single(p: (T) -> Boolean = { true }): T {
  val l = filter(p).take(2).toCollection(ArrayList())
  if (l.size != 1) {
    throw IllegalArgumentException("not exactly one element " + l.size)
  }
  else {
    return l[0]
  }
}

fun <T> Reducible<T>.singleOrNull(p: (T) -> Boolean = { true }): T? {
  val l = filter(p).take(2).toCollection(ArrayList())
  return when {
    l.size > 1 ->
      error("more than one element " + l.size)
    l.size == 1 -> l[0]
    else -> null
  }
}

fun <T> Reducible<T>.any(p: (T) -> Boolean = { true }): Boolean = firstOrNull(p) != null

fun <T> Reducible<T>.filter(p: (T) -> Boolean = { true }): Reducible<T> =
  reducible {
    forEach { t ->
      if (p(t)) {
        emit(t)
      }
      else {
        ReduceDecision.Continue
      }
    }
  }

fun <T, C : MutableCollection<T>> Reducible<T>.toCollection(c: C): C {
  forEach { t ->
    c.add(t)
    ReduceDecision.Continue
  }
  return c
}

fun <T> Reducible<T>.toList(): List<T> {
  return toCollection(ArrayList())
}

fun <K, V> Reducible<Pair<K, V>>.toMap(m: MutableMap<K, V> = HashMap()): Map<K, V> {
  forEach { (k, v) ->
    m.put(k, v)
    ReduceDecision.Continue
  }
  return m
}

fun Reducible<*>.count(): Int {
  var i = 0
  forEach {
    i++
    ReduceDecision.Continue
  }
  return i
}