// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <R, U : R, T : R> T.letIf(condition: Boolean, block: (T) -> U): R {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return if (condition) block(this) else this
}

@OptIn(ExperimentalContracts::class)
inline fun <R, U : R, T : R, V> T.letIfNotNull(value: V?, block: (T, V) -> U): R {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return if (value != null) block(this, value) else this
}

inline fun <T> runIf(condition: Boolean, block: () -> T): T? = if (condition) block() else null

fun <T> List<T>.indexOfOrNull(element: T): Int? {
  val index = indexOf(element)
  return if (index < 0) {
    null
  }
  else {
    index
  }
}

fun <A, B> Iterable<A>.zipWithNulls(other: Iterable<B>): Iterable<Pair<A?, B?>> {
  val iter1 = this.iterator()
  val iter2 = other.iterator()

  val list = ArrayList<Pair<A?, B?>>()
  while (iter1.hasNext() || iter2.hasNext()) {
    val v1 = if (iter1.hasNext()) iter1.next() else null
    val v2 = if (iter2.hasNext()) iter2.next() else null
    list.add(Pair(v1, v2))
  }
  return list
}
