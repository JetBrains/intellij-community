// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public interface GlobalWorkspaceModelCache {
  public fun loadCache(): MutableEntityStorage?
  public fun scheduleCacheSave()
  public fun invalidateCaches()

  public companion object {
    public fun getInstance(): GlobalWorkspaceModelCache? =
      ApplicationManager.getApplication().getService(GlobalWorkspaceModelCache::class.java)
  }
}