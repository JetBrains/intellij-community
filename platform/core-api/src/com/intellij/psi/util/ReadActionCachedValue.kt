// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import org.jetbrains.annotations.ApiStatus

/**
 * A short-living cache bound to the current read-action.
 * It guarantees that cached value will be evaluated only once per read-action and cleared when read-action ends.
 * Also, this cache is thread-local, thus no thread-contention is expected, no synchronisation is needed
 * and there is no requirements for the cached values to be thread-safe.
 * It makes it a light-weight alternative to [CachedValuesManager] with automatic cleanup and low gc impact.
 *
 * In contrast to [CachedValuesManager] this cache doesn't rely on [com.intellij.openapi.util.Key]
 * and behave similar to Guava/Caffeine [LoadingCache](https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/2.2.6/com/github/benmanes/caffeine/cache/LoadingCache.html)
 *
 * For example, if you have:
 *
 * ```kotlin
 * class A {
 *    fun foo(): String = expensiveComputation()
 * }
 * ```
 * you will cache it with [ReadActionCachedValue] the following way:
 *
 * ```kotlin
 * class A {
 *    private val fooCache: ReadActionCachedValue<String> = ReadActionCachedValue { expensiveComputation() }
 *
 *    fun foo(): String = fooCache.getCachedOrEvaluate()
 * }
 * ```
 *
 * @see ReadActionCache
 *
 * @param provider a lambda for computing the cached value. Is called once per read-action.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ReadActionCachedValue<T>(private val provider: () -> T) {
  
  fun getCachedOrEvaluate(): T {
    val processingContext = ReadActionCache.getInstance().processingContext ?: return provider.invoke()
    processingContext.get(this)?.let {
      @Suppress("UNCHECKED_CAST")
      return it as T
    }
    val result = provider.invoke()
    processingContext.put(this as Any, result as Any)
    return result
  }
}