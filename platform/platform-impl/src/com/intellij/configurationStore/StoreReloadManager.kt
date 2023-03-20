// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

interface StoreReloadManager {
  companion object {
    @JvmStatic
    fun getInstance() = service<StoreReloadManager>()
  }

  fun reloadProject(project: Project)

  fun blockReloadingProjectOnExternalChanges()

  fun unblockReloadingProjectOnExternalChanges()

  @ApiStatus.Internal
  fun isReloadBlocked(): Boolean

  @ApiStatus.Internal
  fun scheduleProcessingChangedFiles()

  fun saveChangedProjectFile(file: VirtualFile, project: Project)

  suspend fun reloadChangedStorageFiles()

  fun storageFilesChanged(componentManagerToStorages: Map<ComponentManager, Collection<StateStorage>>)
}