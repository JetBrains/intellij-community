// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel.impl

import com.intellij.platform.externalSystem.impl.dependencySubstitution.impl.MetadataStorageImpl
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.MetadataStorageBridge

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBridge(MetadataStorageImpl)
