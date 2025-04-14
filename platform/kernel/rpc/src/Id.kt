// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc

import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for ids which will be passed from the backend to a frontend,
 * so the frontend may use it later on for other RPC calls.
 *
 * [Id] can be generated on the backend using [com.intellij.platform.kernel.backend.ids.storeGlobally] and then passed to the frontend.
 * Afterward, frontend may make RPC calls using this [Id].
 */
@ApiStatus.Internal
interface Id {
  val uid: UID
}

/**
 * Unique identifier for the [Id].
 * It should be unique across all the [Id] in the backend's application.
 *
 * @see com.intellij.platform.kernel.backend.ids.BackendGlobalIdsManager
 */
typealias UID = Int