// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.collections.immutable.PersistentList
import kotlin.math.min

fun <T> PersistentList<T>.removeLast() = removeAt(lastIndex)
fun <T> PersistentList<T>.setLast(element: T) = set(lastIndex, element)
inline fun <T> PersistentList<T>.updateLast(f: (T) -> T) = set(lastIndex, f(get(lastIndex)))

fun <T> PersistentList<T>.drop(n: Int): PersistentList<T> {
  var result = this
  repeat(min(size, n)) {
    result = result.removeAt(0)
  }
  return result
}