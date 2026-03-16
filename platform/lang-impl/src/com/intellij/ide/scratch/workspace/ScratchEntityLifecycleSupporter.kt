// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import java.util.Objects

class ScratchEntityLifecycleSupporter : WorkspaceEntityLifecycleSupporter<ScratchRootsEntity, ScratchRootsEntityBuilder> {
  override fun getEntityClass(): Class<ScratchRootsEntity> {
    return ScratchRootsEntity::class.java
  }

  override fun createSampleEntity(project: Project): ScratchRootsEntityBuilder? {
    return createScratchRootsEntityForProject(project)
  }

  override fun haveEqualData(first: ScratchRootsEntityBuilder, second: ScratchRootsEntity): Boolean {
    return Objects.equals(first.roots, second.roots)
  }
}