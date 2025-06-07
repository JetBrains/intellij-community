// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

/**
 * Acts in similar way to `takeWhile`, but also includes first value that **does not** satisfy the predicate
 */
fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> {
  return sequence {
    val iterator = iterator()
    while (iterator.hasNext()) {
      val item = iterator.next()
      yield(item)
      if (!predicate(item)) {
        break
      }
    }
  }
}