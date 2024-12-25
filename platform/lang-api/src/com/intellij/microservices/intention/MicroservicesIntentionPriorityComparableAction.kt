package com.intellij.microservices.intention

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

/**
 * Interface to mark microservices intentions and provide order to them.
 */
@ApiStatus.Internal
@IntellijInternalApi
interface MicroservicesIntentionPriorityComparableAction : Comparable<Any> {
  val microservicesActionPriority: Int

  override fun compareTo(other: Any): Int {
    if (other !is MicroservicesIntentionPriorityComparableAction) return 0
    return -microservicesActionPriority.compareTo(other.microservicesActionPriority)
  }
}