// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@Actual
internal fun threadLocalImplNative(supplier: () -> Any?): ThreadLocal<Any?> = threadLocal(supplier)

private fun <T> threadLocal(supplier: () -> T) = object : ThreadLocal<T> {
  override fun get(): T = ThreadLocalMap[this] as? T ?: supplier().also { set(it) }
  override fun set(value: T) { ThreadLocalMap[this] = value }
  override fun remove() { ThreadLocalMap.remove(this) }
}

@kotlin.native.concurrent.ThreadLocal
private object ThreadLocalMap: MutableMap<Any, Any?> by mutableMapOf()