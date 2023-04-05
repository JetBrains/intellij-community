// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.indexing.EntityIndexingServiceEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleRootListenerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange

internal object ModuleRootListenerBridgeImpl : ModuleRootListenerBridge {
  override fun fireBeforeRootsChanged(project: Project, event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    if (VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
      //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
      return
    }
    
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) projectRootManager.rootsChanged.beforeRootsChanged()
  }

  override fun fireRootsChanged(project: Project, event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    if (VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
      //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
      return
    }

    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) {
      val rootsChangeInfo = EntityIndexingServiceEx.getInstanceEx().createWorkspaceChangedEventInfo(event.getAllChanges().toList())
      projectRootManager.rootsChanged.rootsChanged(rootsChangeInfo)
    }
  }

  private fun shouldFireRootsChanged(events: VersionedStorageChange, project: Project): Boolean {
    val indexingService = EntityIndexingServiceEx.getInstanceEx()
    return events.getAllChanges().any {
      val entity = when (it) {
        is EntityChange.Added -> it.entity
        is EntityChange.Removed -> it.entity
        is EntityChange.Replaced -> it.newEntity
      }
      indexingService.shouldCauseRescan(entity, project)
    }
  }
}