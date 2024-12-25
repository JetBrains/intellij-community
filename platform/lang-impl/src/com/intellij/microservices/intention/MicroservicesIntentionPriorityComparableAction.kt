// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.intention

import org.jetbrains.annotations.ApiStatus

/**
 * Interface to mark microservices intentions and provide order to them.
 */
@ApiStatus.Experimental
interface MicroservicesIntentionPriorityComparableAction : Comparable<Any> {
  val microservicesActionPriority: Int

  override fun compareTo(other: Any): Int {
    if (other !is MicroservicesIntentionPriorityComparableAction) return 0
    return -microservicesActionPriority.compareTo(other.microservicesActionPriority)
  }
}