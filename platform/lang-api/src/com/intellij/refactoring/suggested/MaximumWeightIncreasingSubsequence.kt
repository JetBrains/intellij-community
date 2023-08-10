// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.util.containers.IntArrayList
import org.jetbrains.annotations.ApiStatus

/**
 * Finds an increasing subsequence of element in the list with the maximum sum of [weightFunction] for all elements.
 * @return a list of indices in [list] of the elements of the subsequence found.
 */
@ApiStatus.Internal
fun <T : Comparable<T>> findMaximumWeightIncreasingSubsequence(list: List<T>, weightFunction: (T) -> Double): IntArray {
  if (list.isEmpty()) return intArrayOf()
  if (list.size == 1) return intArrayOf(0)

  // subsequenceLastButOneItems[i] is the index of the last but one item in the maximum weight subsequence
  // which ends at the element with index i or -1 if the subsequence has only one element
  // (the index of the last item is always i)
  val subsequenceLastButOneItems = IntArray(list.size) { -1 }

  // subsequenceWeight[i] is the weight of the maximum weight subsequence which ends at the element with index i
  val subsequenceWeight = DoubleArray(list.size)

  for (i in list.indices) {
    val elementWeight = weightFunction(list[i])
    require(elementWeight > 0)
    subsequenceWeight[i] = elementWeight

    for (j in 0 until i) {
      if (list[i] > list[j] && subsequenceWeight[i] - elementWeight < subsequenceWeight[j]) {
        subsequenceLastButOneItems[i] = j
        subsequenceWeight[i] = subsequenceWeight[j] + elementWeight
      }
    }
  }

  val result = IntArrayList()
  var index = list.indices.maxByOrNull { subsequenceWeight[it] }!!
  while (index >= 0) {
    result.add(index)
    index = subsequenceLastButOneItems[index]
  }
  return result.toArray().reversedArray()
}
