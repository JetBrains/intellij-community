// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel

import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ExternalProjectEntity: WorkspaceEntityWithSymbolicId {

  val externalProjectPath: String

  override val symbolicId: ExternalProjectEntityId
    get() = ExternalProjectEntityId(externalProjectPath)
}
