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
  fun <T> lazy(supplier: () -> T): Supplier<T> {
    val lazy = SynchronizedClearableLazy(supplier)
    registeredLazyValues.add(lazy)
    return lazy
  }

  fun cleanupCachedFields() {
    registeredLazyValues.forEach { it.drop() } // hot path, do not use method reference
  }
}
