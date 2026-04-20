// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.rpc.RemoteApi
import kotlinx.coroutines.CopyableThrowable
import org.jetbrains.annotations.ApiStatus

/**
 * Service that can be accessed through RPC, but that is limited to the lifecycle of a resource.
 */
@ApiStatus.Internal
interface RemoteResource : RemoteApi<Unit>

@ApiStatus.Internal
class RemoteResourceConsumedException(cause: Throwable? = null)
  : RuntimeException("Resource was already consumed", cause),
    CopyableThrowable<RemoteResourceConsumedException> {

  override fun createCopy(): RemoteResourceConsumedException {
    return RemoteResourceConsumedException(this)
  }
}