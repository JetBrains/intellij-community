// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@Actual
internal fun threadLocalImplJvm(supplier: () -> Any?): ThreadLocal<Any?> = threadLocal(supplier)

private fun <T> threadLocal(supplier: (() -> T)) = object : ThreadLocal<T> {
  val threadLocal = java.lang.ThreadLocal.withInitial(supplier)

  override fun get(): T = threadLocal.get()

  override fun remove() = threadLocal.remove()

  override fun set(value: T) = threadLocal.set(value)
}
