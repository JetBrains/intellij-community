// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.zombie.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface RemoteManagedCacheApi : RemoteApi<Unit> {
  suspend fun get(cacheId: CacheId, key: RemoteManagedCacheDto): RemoteManagedCacheDto?
  suspend fun put(cacheId: CacheId, key: RemoteManagedCacheDto, value: RemoteManagedCacheDto?)
  // Used for linearization on creation & pre-fetching
  suspend fun createPrefetchFlow(cacheId: CacheId): Flow<PrefetchedRemoteCacheValue>

  companion object {
    suspend fun getInstance(): RemoteManagedCacheApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteManagedCacheApi>())
    }
  }
}

