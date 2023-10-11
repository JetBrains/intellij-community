// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance

class ScratchWorkspaceStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    RootType.ROOT_EP.addChangeListener({
                                         getEntityBuilderIfNeeded(project)?.also { builder ->
                                           ApplicationManager.getApplication().invokeAndWait {
                                             WriteAction.run<RuntimeException> { writeBuilder(builder, project) }
                                           }
                                         }
                                       }, project)

    getEntityBuilderIfNeeded(project)?.also { builder -> writeAction { writeBuilder(builder, project) } }
  }
}

private fun writeBuilder(builder: MutableEntityStorage, project: Project) {
  WorkspaceModel.getInstance(project).updateProjectModel("ScratchWorkspaceStartupActivity") { tempBuilder: MutableEntityStorage ->
    tempBuilder.addDiff(builder)
  }
}

private fun getEntityBuilderIfNeeded(project: Project): MutableEntityStorage? {
  val snapshot: EntityStorageSnapshot = WorkspaceModel.getInstance(project).currentSnapshot
  val oldEntities: List<ScratchRootsEntity> = snapshot.entities(ScratchRootsEntity::class.java).toList()

  val workspaceIntegrationEnabled = ScratchFileService.isWorkspaceModelIntegrationEnabled()

  if (!workspaceIntegrationEnabled) {
    if (oldEntities.isEmpty()) {
      return null
    }
    val builder = snapshot.toBuilder()
    for (oldEntity in oldEntities) {
      builder.removeEntity(oldEntity)
    }
    return builder
  }

  //workspace integration is enabled
  val scratchFileService = ScratchFileService.getInstance()
  val urlManager = VirtualFileUrlManager.getInstance(project)
  val urls = RootType.getAllRootTypes().filter { !it.isHidden }.map {
    scratchFileService.getRootPath(it)
  }.sorted().map { urlManager.fromPath(it) }.toList()

  if (oldEntities.size == 1) {
    val entity: ScratchRootsEntity = oldEntities[0]
    if (entity.roots == urls) {
      return null
    }
  }

  val builder = snapshot.toBuilder()
  for (oldEntity in oldEntities) {
    builder.removeEntity(oldEntity)
  }
  builder.addEntity(ScratchRootsEntity(urls, ScratchRootsEntitySource))

  return builder
}
