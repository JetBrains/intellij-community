// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual

interface AtomicRef<T> {
  fun get(): T

  fun set(value: T)

  fun updateAndGet(f: (T) -> T): T

  fun getAndUpdate(f: (T) -> T): T

  fun getAndSet(value: T): T

  fun compareAndSet(old: T, new: T): Boolean

  fun compareAndExchange(old: T, new: T): T
}

@Suppress("UNCHECKED_CAST")
fun <T> AtomicRef(value: T): AtomicRef<T> = atomicRefImpl(value) as AtomicRef<T>

fun <T> AtomicRef(): AtomicRef<T?> = AtomicRef(null)

internal fun atomicRefImpl(value: Any?): AtomicRef<Any?> = linkToActual()