// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.zombie.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface RemoteManagedCacheApi : RemoteApi<Unit> {
  suspend fun get(cacheId: CacheId, key: Int): RemoteManagedCacheValueDto?
  suspend fun put(cacheId: CacheId, key: Int, value: RemoteManagedCacheValueDto?)
  // Used for linearization on project load
  suspend fun create(cacheId: CacheId)

  companion object {
    suspend fun getInstance(): RemoteManagedCacheApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteManagedCacheApi>())
    }
  }
}

