// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path


/**
 * Since we have several `GlobalWorkspaceModel`s, we need to separate their cache files.
 * The local `GlobalWorkspaceModel` corresponds to id "Local".
 */
@ApiStatus.Internal
public interface GlobalWorkspaceModelCache {
  public fun cacheFile(environmentName: InternalEnvironmentName): Path
  public fun loadCache(environmentName: InternalEnvironmentName): MutableEntityStorage?
  public fun scheduleCacheSave()

  @TestOnly
  public suspend fun saveCacheNow()

  public fun invalidateCaches()

  public fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)
  public fun registerCachePartition(environmentName: InternalEnvironmentName)

  public companion object {
    public fun getInstance(): GlobalWorkspaceModelCache? {
      return ApplicationManager.getApplication().getService(GlobalWorkspaceModelCache::class.java)
    }
  }
}