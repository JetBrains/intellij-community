// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a wrapper for an ephemeral value with a limited lifetime.
 */
@ApiStatus.Experimental
interface Ephemeral<out T : Any> {
  /**
   * Suspends until the ephemeral value held by this instance is available and returns it, or `null` if the value has expired.
   */
  suspend fun unwrap(): T?

  /**
   * Returns a long-living `Flow` that tracks the ephemeral value lifecycle.
   *
   * Flow behavior:
   * - Starts with `null`
   * - Emits the value when available
   * - Emits `null` when expired
   * - Can re-emit the value if it becomes available again
   *
   * The flow can outlive the original `Ephemeral` instance. Exceptions are logged and treated as `null`.
   *
   * @return A `Flow<T?>` where `null` indicates unavailable value
   */
  fun asFlow(): Flow<T?>

  /**
   * Creates a new `Ephemeral` instance by transforming the value held by the current instance
   * using the provided mapping function. The resulting value inherits the original lifetime.
   */
  fun <P : Any> derive(map: (T) -> P?): Ephemeral<P>
}

internal class StaticEphemeral<T : Any>(private val data: T?) : Ephemeral<T> {
  override suspend fun unwrap(): T? =
    data

  override fun asFlow(): Flow<T?> =
    flowOf(data)

  override fun <P : Any> derive(map: (T) -> P?): Ephemeral<P> =
    StaticEphemeral(data?.let(map))
}