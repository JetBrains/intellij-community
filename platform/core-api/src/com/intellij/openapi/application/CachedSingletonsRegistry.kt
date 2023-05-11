// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

@ApiStatus.Internal
object CachedSingletonsRegistry {
  private val registeredLazyValues = CopyOnWriteArrayList<SynchronizedClearableLazy<*>>()

  @JvmStatic
  fun <T> lazy(supplier: () -> T): Supplier<T> {
    val lazy = SynchronizedClearableLazy(supplier)
    registeredLazyValues.add(lazy)
    return lazy
  }

  fun <T : Any> lazyWithNullProtection(supplier: () -> T?): (Boolean) -> T? {
    val lazy = SynchronizedClearableLazy(supplier)
    registeredLazyValues.add(lazy)
    return { createIfNeeded ->
      if (createIfNeeded) {
        lazy.get() ?: supplier()
      }
      else {
        lazy.valueIfInitialized
      }
    }
  }

  fun cleanupCachedFields() {
    registeredLazyValues.forEach(Consumer(SynchronizedClearableLazy<*>::drop))
  }
}
