// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace.impl

import com.intellij.ide.ApplicationActivity
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer

/**
 * Loads and initializes the classes of the cache serializer on a background thread at start-up,
 * so the first project cache load on the project open path does not pay for them.
 */
internal class WorkspaceModelCacheSerializerWarmUp : ApplicationActivity {
  override suspend fun execute() {
    EntityStorageSerializerImpl.warmUp(WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver)
  }
}
