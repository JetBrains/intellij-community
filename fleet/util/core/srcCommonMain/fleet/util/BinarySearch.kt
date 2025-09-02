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