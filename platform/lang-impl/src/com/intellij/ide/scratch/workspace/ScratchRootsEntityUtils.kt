// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance

internal fun createScratchRootsEntityForProject(project: Project): ScratchRootsEntity? {
  if (!ScratchFileService.isWorkspaceModelIntegrationEnabled()) return null
  val scratchFileService = ScratchFileService.getInstance()
  val urlManager = VirtualFileUrlManager.getInstance(project)
  val urls = RootType.getAllRootTypes().filter { !it.isHidden }.map {
    scratchFileService.getRootPath(it)
  }.sorted().map { urlManager.fromUrl(VfsUtilCore.pathToUrl(it)) }.toList()

  return ScratchRootsEntity(urls, ScratchRootsEntitySource)
}