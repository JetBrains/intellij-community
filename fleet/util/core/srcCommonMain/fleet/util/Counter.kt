// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch


private val atomicCounter = AtomicLong(0)

/**
 * return a new unique long value among all invocations of this function
  */
fun nextLongValue(): Long {
  return atomicCounter.incrementAndFetch()
}