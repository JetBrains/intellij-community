// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

@ApiStatus.Internal
object CachedSingletonsRegistry {
  private val registeredLazyValues = CopyOnWriteArrayList<SynchronizedClearableLazy<*>>()

  @JvmStatic
  fun <T> lazy(supplier: Supplier<T>): Supplier<T> {
    val lazy = SynchronizedClearableLazy { supplier.get() }
    registeredLazyValues.add(lazy)
    return Supplier {
      val result: T? = lazy.get()
      result ?: supplier.get()
    }
  }

  @Deprecated("Do not use.")
  @JvmStatic
  fun <T> markCachedField(@Suppress("unused") klass: Class<T>): T? {
    return null
  }

  @JvmStatic
  fun cleanupCachedFields() {
    val iterator = registeredLazyValues.iterator()
    while (iterator.hasNext()) {
      iterator.next().drop()
    }
  }
}
