// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.ids

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an association between [Id] and value which are going to be stored in application storage
 * through [storeValueGlobally].
 *
 * [BackendValueIdType] provides a type-safe way of putting and acquiring values by [storeValueGlobally] and [findValueById] methods.
 *
 * To store values in application storage, you need:
 *   1. Introduce id in the shared code like `data class SomeId(override val uid: UID): Id`
 *   2. Introduce record type which will be used by [storeValueGlobally] and [findValueById] methods
 *      like: `object SomeBackendValueIdType: BackendValueIdType<SomeId, SomeClass>(::SomeId)`
 * Now you can use this ValueIdType to store values like:
 * ```kotlin
 * val id = storeValueGlobally(coroutineScope, someValue, SomeBackendValueIdType)
 * val someValue2 = findById(id, SomeBackendValueIdType)
 * ```
 */
@ApiStatus.Internal
abstract class BackendValueIdType<TID : Id, Value : Any>(
  internal val idFactory: (UID) -> TID,
)