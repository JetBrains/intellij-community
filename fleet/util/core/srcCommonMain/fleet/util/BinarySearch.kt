package fleet.util

import kotlin.math.abs


fun binarySearchInRange(from: Float, to: Float, predicate: (Float) -> Boolean): Float {
  var l = from
  var r = to
  while (abs(l - r) > 1e-6f) {
    val m = (r + l) / 2f
    if (predicate(m)) {
      l = m
    }
    else {
      r = m
    }
  }
  return r
}

fun IntArray.binarySearch(element: Int): Int {
  var l = 0
  var r = size - 1

  while (l <= r) {
    val m = (l + r) / 2
    val midElement = get(m)

    if (midElement < element) {
      l = m + 1
    }
    else if (midElement > element) {
      r = m - 1
    }
    else {
      return m
    }
  }
  return -(l + 1)
}


fun LongArray.binarySearch(element: Long): Int {
  var l = 0
  var r = size - 1

  while (l <= r) {
    val m = (l + r) / 2
    val midElement = get(m)

    if (midElement < element) {
      l = m + 1
    }
    else if (midElement > element) {
      r = m - 1
    }
    else {
      return m
    }
  }
  return -(l + 1)
}
