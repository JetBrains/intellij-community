package com.jetbrains.fleet.rpc.plugin.ir

/**
 * Same idea as [kotlin.collections.singleOrNull] but will throw if the collection contains more than one element.
 * */
fun <T> Iterable<T>.singleOrNullOrThrow(p: (T) -> Boolean = { true }): T? {
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
