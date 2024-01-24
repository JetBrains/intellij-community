// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Plugins aren't supposed to use this interface directly, the cache is loaded and saved automatically by [WorkspaceModel].
 */
@ApiStatus.Internal
public interface WorkspaceModelCache {
  public val enabled: Boolean

  public fun loadCache(): MutableEntityStorage?
  public fun loadUnloadedEntitiesCache(): MutableEntityStorage?

  public fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)

  /**
   * Save workspace model caches
   */
  @TestOnly
  public fun saveCacheNow()

  public companion object {
    public fun getInstance(project: Project): WorkspaceModelCache? = project.getService(WorkspaceModelCache::class.java)?.takeIf { it.enabled }
  }
}
