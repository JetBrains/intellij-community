// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public interface WorkspaceModelSerializerHook {
  public fun beforeSerialization(entityStorage: ImmutableEntityStorage) : ImmutableEntityStorage
  public fun beforeUnloadedSerialization(entityStorage: ImmutableEntityStorage) : ImmutableEntityStorage = beforeSerialization(entityStorage)
}