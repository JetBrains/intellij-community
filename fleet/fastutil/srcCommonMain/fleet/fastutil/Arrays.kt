// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil

import fleet.fastutil.ints.IntList
import fleet.fastutil.longs.LongList

object Arrays {

  const val MAX_ARRAY_SIZE: Int = Int.MAX_VALUE - 8

  fun ensureOffsetLength(arrayLength: Int, offset: Int, length: Int) {
    check(arrayLength >= 0)

    if (offset < 0) throw IndexOutOfBoundsException("Offset ($offset) is negative")
    if (length < 0) throw IllegalArgumentException("Length ($length) is negative")
    if (length > arrayLength - offset) throw IndexOutOfBoundsException("Last index (" + (offset.toLong() + length) + ") is greater than array length (" + arrayLength + ")")
  }

  fun ensureOffsetLength(a: IntList, offset: Int, length: Int) {
    ensureOffsetLength(a.size, offset, length)
  }

  fun ensureOffsetLength(a: IntArray, offset: Int, length: Int) {
    ensureOffsetLength(a.size, offset, length)
  }


  fun ensureOffsetLength(a: LongList, offset: Int, length: Int) {
    ensureOffsetLength(a.size, offset, length)
  }

  fun ensureOffsetLength(a: LongArray, offset: Int, length: Int) {
    ensureOffsetLength(a.size, offset, length)
  }



  fun ensureFromTo(arrayLength: Int, from: Int, to: Int) {
    check(arrayLength >= 0)
    if (from < 0) throw IndexOutOfBoundsException("Start index ($from) is negative")
    if (from > to) throw IllegalArgumentException("Start index ($from) is greater than end index ($to)")
    if (to > arrayLength) throw IndexOutOfBoundsException("End index ($to) is greater than array length ($arrayLength)")
  }
}

fun <T> Iterator<T>.skip(n: Int): Int {
  if (n < 0) throw IllegalArgumentException("Argument must be nonnegative: $n")
  var i = n
  while (i-- != 0 && hasNext()) next()
  return n - i - 1
}