// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.indexing.EntityIndexingServiceEx
import com.intellij.util.indexing.ProjectEntityIndexingService
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge

/**
 * Updates data in [WorkspaceFileIndex] and fires rootsChanged events if roots validity was changed.
 */
internal object RootsChangeWatcher {
  private fun getProjectRootManagerToFireEvent(project: Project): ProjectRootManagerBridge? {
    if (project.isDisposed) return null
    val projectRootManager = ProjectRootManager.getInstance(project) as? ProjectRootManagerBridge
    return projectRootManager?.takeUnless { it.isFiringEvent }
  }
  
  internal fun prepareChange(events: List<VFileEvent>, project: Project): AsyncFileListener.ChangeApplier? {
    val fileIndex = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx
    val applier = fileIndex.indexData.analyzeVfsChanges(events) ?: return null
    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        applier.beforeVfsChange()
        getProjectRootManagerToFireEvent(project)?.rootsChanged?.beforeRootsChanged()
      }

      override fun afterVfsChange() {
        applier.afterVfsChange()
        val projectRootManager = getProjectRootManagerToFireEvent(project)
        if (projectRootManager != null) {
          val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
          //todo indexing should automatically schedule indexing for newly registered WorkspaceFileSet instead of determining entities manually 
          val entitiesToReindex = applier.entitiesToReindex.filter { 
            val entity = it.resolve(snapshot)
            entity != null && ProjectEntityIndexingService.getInstance(project).shouldCauseRescan(null, entity)
          }
          val indexingInfo = EntityIndexingServiceEx.getInstanceEx().createWorkspaceEntitiesRootsChangedInfo(entitiesToReindex)
          projectRootManager.rootsChanged.rootsChanged(indexingInfo)
        }
      }
    }
  }
}
