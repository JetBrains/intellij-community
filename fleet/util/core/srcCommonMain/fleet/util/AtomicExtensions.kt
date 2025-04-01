// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.multiplatform.shims.AtomicRef

fun AtomicRef<Int>.getAndIncrement(): Int = getAndUpdate { it + 1 }
fun AtomicRef<Int>.incrementAndGet(): Int = updateAndGet { it + 1 }
fun AtomicRef<Long>.getAndIncrement(): Long = getAndUpdate { it + 1 }
fun AtomicRef<Long>.incrementAndGet(): Long = updateAndGet { it + 1 }

fun AtomicRef<Int>.getAndDecrement(): Int = getAndUpdate { it - 1 }
fun AtomicRef<Int>.decrementAndGet(): Int = updateAndGet { it - 1 }
fun AtomicRef<Long>.getAndDecrement(): Long = getAndUpdate { it - 1 }
fun AtomicRef<Long>.decrementAndGet(): Long = updateAndGet { it - 1 }

fun AtomicRef<Int>.getAndAdd(delta: Int): Int = getAndUpdate { it + delta }
fun AtomicRef<Int>.addAndGet(delta: Int): Int = updateAndGet { it + delta }
fun AtomicRef<Long>.getAndAdd(delta: Long): Long = getAndUpdate { it + delta }
fun AtomicRef<Long>.addAndGet(delta: Long): Long = updateAndGet { it + delta }