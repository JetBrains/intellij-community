// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Hides the mutable state [hidden] and gives access to it only inside [invoke], under `synchronized` on it.
 *
 * ```kotlin
 * private val criticalSection = CriticalSection(object {
 *   val registry: MutableMap<URI, IjentFileSystemApi> = hashMapOf()
 * })
 *
 * criticalSection { registry[uri] }
 * ```
 *
 * The body runs exactly once, so it can initialize a `val` of the caller. It must not suspend: take a snapshot
 * inside the critical section and do the slow work outside.
 */
@ApiStatus.Internal
@JvmInline
value class CriticalSection<D : Any>(val hidden: D) {
  @OptIn(ExperimentalContracts::class)
  inline operator fun <T> invoke(body: D.() -> T): T {
    contract {
      callsInPlace(body, InvocationKind.EXACTLY_ONCE)
    }
    return synchronized(hidden) {
      hidden.body()
    }
  }
}
