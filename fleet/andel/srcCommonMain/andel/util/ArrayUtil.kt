// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.util

fun IntArray.chunked(chunk: Int): List<IntArray> {
  if (size < chunk) return listOf(this)
  val result = mutableListOf<IntArray>()
  var index = 0
  while (index < size) {
    result.add(copyOfRange(index, (index + chunk).coerceAtMost(size)))
    index += chunk
  }
  return result
}

fun IntArray.replace(fromIndex: Int, toIndex: Int, ints: IntArray): IntArray {
  val newLength = this.size - (toIndex - fromIndex) + ints.size
  val result = IntArray(newLength)
  this.copyInto(result, 0, 0, fromIndex)
  ints.copyInto(result, fromIndex, 0, ints.size)
  this.copyInto(result, fromIndex + ints.size, toIndex, this.size)
  return result
}
