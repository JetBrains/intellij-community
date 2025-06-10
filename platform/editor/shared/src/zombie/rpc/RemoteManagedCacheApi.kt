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
  suspend fun get(grave: ZombieCacheId, zombieId: Int): FingerprintedZombieDto?
  suspend fun put(grave: ZombieCacheId, zombieId: Int, zombie: FingerprintedZombieDto?)
  // Used for linearization on project load
  suspend fun create(grave: ZombieCacheId)

  companion object {
    suspend fun getInstance(): RemoteManagedCacheApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteManagedCacheApi>())
    }
  }
}

