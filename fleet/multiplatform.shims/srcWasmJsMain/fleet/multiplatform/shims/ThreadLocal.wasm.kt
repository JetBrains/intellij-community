// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@Actual
internal fun threadLocalImplWasmJs(supplier: () -> Any?): ThreadLocal<Any?> = threadLocal(supplier)

private fun <T> threadLocal(supplier: () -> T) = object : ThreadLocal<T> {
  private var value: Any? = null

  @Suppress("UNCHECKED_CAST")
  override fun get(): T {
    return when (val currentValue = value) {
      null -> supplier().also { set(it) }
      NullValue -> null as T
      else -> currentValue as T
    }
  }

  override fun remove() {
    value = null
  }

  override fun set(value: T) {
    this.value = value ?: NullValue
  }
}

private object NullValue
