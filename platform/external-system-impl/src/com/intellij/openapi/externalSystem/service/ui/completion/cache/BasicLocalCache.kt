// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion.cache

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class BasicLocalCache<T> {

  private val atomic = AtomicReference<CachedValue<T>?>()

  fun getValue(): T? {
    return atomic.get()?.value
  }

  fun getOrCreateValue(stamp: Long, createValue: () -> T): T {
    val cachedValue = atomic.updateAndGet {
      if (it == null || it.stamp < stamp) {
        CachedValue(stamp, createValue())
      }
      else {
        it
      }
    }!!
    return cachedValue.value
  }

  private class CachedValue<T>(val stamp: Long, val value: T)
}