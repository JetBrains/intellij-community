// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@ApiStatus.Internal
public interface GlobalWorkspaceModelCache {
  public val cacheFile: Path
  public fun loadCache(): MutableEntityStorage?
  public fun scheduleCacheSave()
  @TestOnly
  public suspend fun saveCacheNow();
  public fun invalidateCaches()

  public fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)

  public companion object {
    @JvmStatic
    public fun getInstance(): GlobalWorkspaceModelCache? =
      ApplicationManager.getApplication().getService(GlobalWorkspaceModelCache::class.java)
  }
}