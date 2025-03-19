// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel

internal fun createScratchRootsEntityForProject(project: Project): ScratchRootsEntity.Builder? {
  if (!ScratchFileService.isWorkspaceModelIntegrationEnabled()) return null
  val scratchFileService = ScratchFileService.getInstance()
  val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
  val urls = RootType.getAllRootTypes().filter { !it.isHidden }.map {
    scratchFileService.getRootPath(it)
  }.sorted().map { urlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(it)) }.toList()

  return ScratchRootsEntity(urls, ScratchRootsEntitySource)
}