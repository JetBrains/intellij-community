// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path


/**
 * Since we have several `GlobalWorkspaceModel`s, we need to separate their cache files.
 * The local `GlobalWorkspaceModel` corresponds to id "Local".
 */
@ApiStatus.Internal
public interface GlobalWorkspaceModelCache {
  public fun cacheFile(id: InternalEnvironmentName): Path
  public fun loadCache(id: InternalEnvironmentName): MutableEntityStorage?
  public fun scheduleCacheSave()
  @TestOnly
  public suspend fun saveCacheNow();
  public fun invalidateCaches()

  public fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)
  public fun registerCachePartition(id: InternalEnvironmentName)

  /**
   * We could associate the name of environment with `EelDescriptor`.
   * However, we are not sure that we want to expose eel as a dependency of the API module with workspace classes,
   * hence we abstract `EelDescriptor` to a mere string [name].
   */
  @ApiStatus.Internal
  public interface InternalEnvironmentName {
    public val name: @NonNls String
  }

  public companion object {
    @JvmStatic
    public fun getInstance(): GlobalWorkspaceModelCache? =
      ApplicationManager.getApplication().getService(GlobalWorkspaceModelCache::class.java)
  }
}