// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.RootsChangeRescanningInfo

internal class EntityIndexingServiceImpl : EntityIndexingServiceEx {

  override fun createWorkspaceChangedEventInfo(): RootsChangeRescanningInfo {
    return WorkspaceEventRescanningInfo
  }

  override fun createWorkspaceEntitiesRootsChangedInfo(): RootsChangeRescanningInfo {
    return WorkspaceEntitiesRootsChangedRescanningInfo
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

  internal object WorkspaceEventRescanningInfo : RootsChangeRescanningInfo

  internal object WorkspaceEntitiesRootsChangedRescanningInfo : RootsChangeRescanningInfo

}