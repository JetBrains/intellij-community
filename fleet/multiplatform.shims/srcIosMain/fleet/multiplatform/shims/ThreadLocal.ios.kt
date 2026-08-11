// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@Actual
internal fun threadLocalImplNative(supplier: () -> Any?): ThreadLocal<Any?> = threadLocal(supplier)

private fun <T> threadLocal(supplier: () -> T) = object : ThreadLocal<T> {
  @Suppress("UNCHECKED_CAST")
  override fun get(): T {
    return when (val value = ThreadLocalMap[this]) {
      null -> supplier().also { set(it) }
      NullValue -> null as T
      else -> value as T
    }
  }

  override fun set(value: T) {
    ThreadLocalMap[this] = value ?: NullValue
  }

  override fun remove() { ThreadLocalMap.remove(this) }
}

private object NullValue

@kotlin.native.concurrent.ThreadLocal
private object ThreadLocalMap: MutableMap<Any, Any?> by mutableMapOf()