// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.projectImport.ProjectEntitiesAttacher
import com.intellij.workspaceModel.ide.ProjectRootEntitySource
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectRootEntityAttacher : ProjectEntitiesAttacher {
  override fun extractEntitiesToAttach(storage: EntityStorage): MutableEntityStorage {
    return MutableEntityStorage.create().also { newStorage ->
      newStorage.replaceBySource({ it is ProjectRootEntitySource }, storage)
    }
  }
}
