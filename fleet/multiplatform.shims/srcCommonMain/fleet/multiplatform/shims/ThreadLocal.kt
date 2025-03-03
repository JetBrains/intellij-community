// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual

interface ThreadLocal<T> {
  fun get(): T

  fun remove()

  fun set(value: T)
}

fun <T> ThreadLocal(): ThreadLocal<T?> = ThreadLocal { null }

fun <T> ThreadLocal(supplier: () -> T): ThreadLocal<T> = threadLocalImpl(supplier) as ThreadLocal<T>

internal fun threadLocalImpl(supplier: () -> Any?): ThreadLocal<Any?> = linkToActual()