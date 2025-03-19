// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity

internal class EntityIndexingServiceImpl : EntityIndexingServiceEx {

  override fun createBuildableInfoBuilder(): BuildableRootsChangeRescanningInfo {
    return BuildableRootsChangeRescanningInfoImpl()
  }

  override fun createWorkspaceChangedEventInfo(changes: List<EntityChange<*>>): RootsChangeRescanningInfo {
    return WorkspaceEventRescanningInfo(changes)
  }

  override fun createWorkspaceEntitiesRootsChangedInfo(pointers: List<EntityPointer<WorkspaceEntity>>): RootsChangeRescanningInfo {
    return WorkspaceEntitiesRootsChangedRescanningInfo(pointers)
  }

  override fun isFromWorkspaceOnly(indexingInfos: List<RootsChangeRescanningInfo>): Boolean {
    if (indexingInfos.isEmpty()) return false
    for (info in indexingInfos) {
      if (info !is WorkspaceEventRescanningInfo) {
        return false
      }
    }
    return true
  }

  internal class WorkspaceEventRescanningInfo(val events: List<EntityChange<*>>) : RootsChangeRescanningInfo

  internal class WorkspaceEntitiesRootsChangedRescanningInfo(val pointers: List<EntityPointer<WorkspaceEntity>>) : RootsChangeRescanningInfo

}