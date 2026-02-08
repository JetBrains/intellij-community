// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface IndexingTestEntity2 : WorkspaceEntity {
  val roots: List<VirtualFileUrl>
  val excludedRoots: List<VirtualFileUrl>
}
